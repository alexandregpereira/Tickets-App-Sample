package com.example.checkout

import app.cash.turbine.test
import com.example.checkout.purchase.PurchaseRepository
import com.example.checkout.purchase.PurchaseStatus
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.StartPaymentUseCase
import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase
import kotlinx.coroutines.awaitCancellation
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

        val order = startPayment.orders.single()
        assertEquals(24_000L, order.totalInCents)
        val item = order.items.single()
        assertEquals("evt-1", item.sku)
        assertEquals(2, item.quantity)
        assertEquals(12_000L, item.unitPriceInCents)
    }

    @Test
    fun `registers the purchase as pending before starting the payment`() = runTest {
        // O pagamento nunca termina: dá para observar o estado no meio do caminho.
        val payment = NeverReturningStartPayment()
        val uiModel = createUiModel(startPayment = payment).alsoLoaded()

        uiModel.onPayClick()

        assertTrue(uiModel.state.value.isPaymentInFlight)
        assertFalse(uiModel.state.value.canPay)
        val purchase = purchaseRepository.find(payment.orders.single().reference)
        assertNotNull(purchase)
        assertEquals(PurchaseStatus.PENDING, purchase!!.status)
    }

    @Test
    fun `ignores a second pay click while a payment is in flight`() = runTest {
        val payment = NeverReturningStartPayment()
        val uiModel = createUiModel(startPayment = payment).alsoLoaded()

        uiModel.onPayClick()
        uiModel.onPayClick()
        uiModel.onPayClick()

        assertEquals(1, payment.orders.size)
    }

    @Test
    fun `records the approval and navigates to the receipt`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.actions.test {
            uiModel.onPayClick()

            val reference = startPayment.orders.single().reference
            // isApproved = true faz a navegação remover o checkout da pilha.
            assertEquals(
                CheckoutUiAction.NavigateToReceipt(reference, isApproved = true),
                awaitItem(),
            )

            val purchase = purchaseRepository.find(reference)!!
            assertEquals(PurchaseStatus.APPROVED, purchase.status)
            assertEquals("140126", purchase.authorizationCode)
            assertEquals("CREDITO A VISTA", purchase.paymentDescription)
            assertFalse(uiModel.state.value.isPaymentInFlight)
        }
    }

    @Test
    fun `records a cancellation and still navigates to the receipt`() = runTest {
        startPayment.nextResult =
            failedResult(PaymentError.CANCELLED_BY_USER, "CANCELADO PELO USUÁRIO")
        val uiModel = createUiModel().alsoLoaded()

        uiModel.actions.test {
            uiModel.onPayClick()

            val reference = startPayment.orders.single().reference
            // isApproved = false mantém o checkout na pilha, para o usuário tentar de novo.
            assertEquals(
                CheckoutUiAction.NavigateToReceipt(reference, isApproved = false),
                awaitItem(),
            )

            val purchase = purchaseRepository.find(reference)!!
            assertEquals(PurchaseStatus.CANCELLED, purchase.status)
            assertEquals("CANCELADO PELO USUÁRIO", purchase.failureReason)
        }
    }

    @Test
    fun `abandoning the payment leaves no purchase and no receipt`() = runTest {
        startPayment.nextResult = failedResult(PaymentError.ABANDONED, "Pagamento não concluído.")
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()

        // Nada foi cobrado: sem registro, sem comprovante e sem mensagem de erro — o usuário só
        // desistiu, e a tela volta a permitir uma nova tentativa.
        assertNull(purchaseRepository.find(startPayment.orders.single().reference))
        assertNull(uiModel.state.value.errorMessage)
        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertTrue(uiModel.state.value.canPay)
        uiModel.actions.test { expectNoEvents() }
    }

    @Test
    fun `explains a missing payment app and keeps no purchase`() = runTest {
        startPayment.nextResult = failedResult(PaymentError.APP_NOT_FOUND, "App de pagamento não encontrado.")
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()

        assertNull(purchaseRepository.find(startPayment.orders.single().reference))
        assertEquals("App de pagamento não encontrado.", uiModel.state.value.errorMessage)
        assertTrue(uiModel.state.value.canPay)
        uiModel.actions.test { expectNoEvents() }
    }

    @Test
    fun `sends the updated quantity after abandoning a payment`() = runTest {
        startPayment.nextResult = failedResult(PaymentError.ABANDONED)
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()

        // O usuário desistiu e mudou de ideia sobre a quantidade.
        uiModel.onIncreaseQuantityClick()
        startPayment.nextResult = approvedResult()
        uiModel.onPayClick()

        assertEquals(2, startPayment.orders.size)
        val (first, second) = startPayment.orders
        assertEquals(12_000L, first.totalInCents)
        assertEquals(24_000L, second.totalInCents)
        assertEquals(2, second.items.single().quantity)

        // Tentativa nova, pedido novo: a compra abandonada não pode continuar registrada.
        assertNotEquals(first.reference, second.reference)
        assertNull(purchaseRepository.find(first.reference))
        assertEquals(2, purchaseRepository.find(second.reference)!!.quantity)
    }

    private fun createUiModel(
        eventId: String = "evt-1",
        startPayment: StartPaymentUseCase = this.startPayment,
    ) = CheckoutUiModel(
        eventId = eventId,
        getEvent = FakeGetEventUseCase(),
        startPayment = startPayment,
        purchaseRepository = purchaseRepository,
    )

    /** Espera o carregamento inicial do evento terminar. */
    private suspend fun CheckoutUiModel.alsoLoaded(): CheckoutUiModel = apply {
        state.test {
            skipItems(1)
            awaitItem()
        }
    }

    /** Mantém o pagamento em andamento para sempre, expondo o estado intermediário. */
    private class NeverReturningStartPayment : StartPaymentUseCase {
        val orders = mutableListOf<PaymentOrder>()

        override suspend fun invoke(order: PaymentOrder): Nothing {
            orders += order
            awaitCancellation()
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
