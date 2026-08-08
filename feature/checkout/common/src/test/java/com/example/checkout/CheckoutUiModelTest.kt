package com.example.checkout

import app.cash.turbine.test
import com.example.checkout.purchase.PurchaseRepository
import com.example.checkout.purchase.PurchaseStatus
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CheckoutUiModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val purchaseRepository = PurchaseRepository()
    private val startPayment = FakeStartPaymentUseCase()
    private val paymentResultSource = FakePaymentResultSource()

    @Test
    fun `loads the selected event`() = runTest {
        val uiModel = createUiModel()

        uiModel.state.test {
            skipItems(1) // estado de carregamento
            val loaded = awaitItem()
            assertEquals("Festival de Verão", loaded.event?.name)
            assertEquals(1, loaded.quantity)
            assertEquals(12_000L, loaded.totalInCents)
        }
    }

    @Test
    fun `clamps the quantity and recalculates the total`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onDecreaseQuantityClick() // já está no mínimo
        assertEquals(1, uiModel.state.value.quantity)

        repeat(3) { uiModel.onIncreaseQuantityClick() }
        assertEquals(4, uiModel.state.value.quantity)
        assertEquals(48_000L, uiModel.state.value.totalInCents)

        repeat(20) { uiModel.onIncreaseQuantityClick() } // teto de 10
        assertEquals(10, uiModel.state.value.quantity)
    }

    @Test
    fun `sends a payment order with the selected quantity and total`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onIncreaseQuantityClick()

        uiModel.onPayClick()

        val order = startPayment.launchedOrders.single()
        assertEquals(24_000L, order.totalInCents)
        val item = order.items.single()
        assertEquals("evt-1", item.sku)
        assertEquals(2, item.quantity)
        assertEquals(12_000L, item.unitPriceInCents)
        assertTrue(uiModel.state.value.isPaymentInFlight)
        assertFalse(uiModel.state.value.canPay)
    }

    @Test
    fun `registers the purchase as pending before starting the payment`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()

        val purchase = purchaseRepository.find(startPayment.launchedOrders.single().reference)
        assertNotNull(purchase)
        assertEquals(PurchaseStatus.PENDING, purchase!!.status)
    }

    @Test
    fun `ignores a second pay click while a payment is in flight`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()
        uiModel.onPayClick()
        uiModel.onPayClick()

        assertEquals(1, startPayment.orders.size)
    }

    @Test
    fun `starts a fresh purchase when retrying after a launch failure`() = runTest {
        startPayment.failNextPayment = true
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()
        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertNotNull(uiModel.state.value.errorMessage)

        uiModel.onPayClick()

        assertEquals(2, startPayment.orders.size)
        val references = startPayment.orders.map { it.reference }
        // O pagamento nem abriu, então não houve cobrança: a tentativa anterior é descartada por
        // inteiro e a nova parte do zero.
        assertNotEquals(references[0], references[1])
        assertNull(purchaseRepository.find(references[0]))
        assertNotNull(purchaseRepository.find(references[1]))
    }

    @Test
    fun `releases the screen when returning from the payment app without a result`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        assertTrue(uiModel.state.value.isPaymentInFlight)

        // Voltar pelo botão do Android não gera retorno nenhum.
        uiModel.onScreenResume()

        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertTrue(uiModel.state.value.canPay)
    }

    @Test
    fun `sends the updated quantity after returning from the payment app`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()

        // O usuário volta do app de pagamento sem concluir e muda de ideia sobre a quantidade.
        uiModel.onScreenResume()
        uiModel.onIncreaseQuantityClick()
        uiModel.onPayClick()

        assertEquals(2, startPayment.launchedOrders.size)
        val (first, second) = startPayment.launchedOrders
        assertEquals(12_000L, first.totalInCents)
        assertEquals(24_000L, second.totalInCents)
        assertEquals(2, second.items.single().quantity)

        // Tentativa nova, pedido novo: a compra abandonada não pode continuar registrada.
        assertNotEquals(first.reference, second.reference)
        assertNull(purchaseRepository.find(first.reference))
        assertEquals(2, purchaseRepository.find(second.reference)!!.quantity)
    }

    @Test
    fun `keeps the pending purchase on resume so a late result is not lost`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = startPayment.launchedOrders.single().reference

        uiModel.onScreenResume()

        // O desfecho pode chegar logo depois: descartar aqui perderia o resultado. A compra
        // abandonada só sai do repositório quando uma nova tentativa começa.
        assertNotNull(purchaseRepository.find(reference))

        uiModel.onPayClick()

        assertNull(purchaseRepository.find(reference))
    }

    @Test
    fun `resuming before any payment does nothing`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onScreenResume()

        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertEquals(0, startPayment.orders.size)
    }

    @Test
    fun `records the approval and navigates to the receipt`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = startPayment.launchedOrders.single().reference

        uiModel.actions.test {
            paymentResultSource.post(approvedResult(reference))

            // isApproved = true faz a navegação remover o checkout da pilha.
            assertEquals(
                CheckoutUiAction.NavigateToReceipt(reference, isApproved = true),
                awaitItem(),
            )
        }

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.APPROVED, purchase.status)
        assertEquals("140126", purchase.authorizationCode)
        assertEquals("CREDITO A VISTA", purchase.paymentDescription)
        assertFalse(uiModel.state.value.isPaymentInFlight)
    }

    @Test
    fun `records a cancellation and still navigates to the receipt`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = startPayment.launchedOrders.single().reference

        uiModel.actions.test {
            paymentResultSource.post(
                PaymentResult.Failed(PaymentError.CANCELLED_BY_USER, "CANCELADO PELO USUÁRIO")
            )

            // isApproved = false mantém o checkout na pilha, para o usuário tentar de novo.
            assertEquals(
                CheckoutUiAction.NavigateToReceipt(reference, isApproved = false),
                awaitItem(),
            )
        }

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.CANCELLED, purchase.status)
        assertEquals("CANCELADO PELO USUÁRIO", purchase.failureReason)
    }

    @Test
    fun `a repeated result does not change an already recorded purchase`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = startPayment.launchedOrders.single().reference

        paymentResultSource.post(approvedResult(reference))
        // O mesmo retorno pode ser entregue mais de uma vez.
        paymentResultSource.post(approvedResult(reference))
        paymentResultSource.post(PaymentResult.Failed(PaymentError.PAYMENT, "Erro no pagamento"))

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.APPROVED, purchase.status)
        assertNull(purchase.failureReason)
    }

    private fun createUiModel(eventId: String = "evt-1") = CheckoutUiModel(
        eventId = eventId,
        getEvent = FakeGetEventUseCase(),
        startPayment = startPayment,
        purchaseRepository = purchaseRepository,
        paymentResultSource = paymentResultSource,
    )

    /** Espera o carregamento inicial do evento terminar. */
    private suspend fun CheckoutUiModel.alsoLoaded(): CheckoutUiModel = apply {
        state.test {
            skipItems(1)
            awaitItem()
        }
    }

    private class FakeGetEventUseCase : GetEventUseCase {
        override suspend fun invoke(eventId: String): Event? {
            // Suspende como suspenderia uma fonte de dados real, para o estado de carregamento
            // existir de fato e poder ser observado.
            delay(1)
            return Event(id = "evt-1", name = "Festival de Verão", priceInCents = 12_000)
                .takeIf { it.id == eventId }
        }
    }
}
