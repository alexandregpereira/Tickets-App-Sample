package com.example.checkout

import com.example.checkout.purchase.PurchaseRepository
import com.example.checkout.purchase.PurchaseStatus
import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Cobre a corrida entre o retorno do pagamento e o `ON_RESUME` da tela.
 *
 * No Android real, `onNewIntent` (que publica o retorno) e `onResume` acontecem na mesma passagem
 * pela main thread, então `onScreenResume` roda **antes** de o coletor processar o resultado. Um
 * [StandardTestDispatcher] reproduz essa ordem: nada é executado até `advanceUntilIdle`.
 */
class CheckoutUiModelResumeRaceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val purchaseRepository = PurchaseRepository()
    private val startPayment = FakeStartPaymentUseCase()
    private val paymentResultSource = FakePaymentResultSource()

    @Test
    fun `does not release the screen when a result is published but not processed yet`() = runTest {
        val uiModel = createUiModel()
        advanceUntilIdle() // carrega o evento
        uiModel.onPayClick()
        advanceUntilIdle()
        val reference = startPayment.launchedOrders.single().reference

        paymentResultSource.post(approvedResult(reference))
        // Ainda na mesma passagem pela main thread: o coletor não rodou.
        uiModel.onScreenResume()

        assertTrue(
            "o desfecho está a caminho, a tela deve seguir aguardando",
            uiModel.state.value.isPaymentInFlight,
        )

        advanceUntilIdle()

        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertEquals(PurchaseStatus.APPROVED, purchaseRepository.find(reference)!!.status)
    }

    @Test
    fun `a result delivered after the screen was released still reaches the receipt`() = runTest {
        val uiModel = createUiModel()
        advanceUntilIdle()
        uiModel.onPayClick()
        advanceUntilIdle()
        val reference = startPayment.launchedOrders.single().reference

        // O sistema pode entregar o ON_RESUME antes da Intent de retorno: a tela é liberada, mas o
        // desfecho ainda está a caminho e não pode ser perdido.
        uiModel.onScreenResume()
        advanceUntilIdle()
        paymentResultSource.post(approvedResult(reference))
        advanceUntilIdle()

        assertEquals(PurchaseStatus.APPROVED, purchaseRepository.find(reference)!!.status)
    }

    @Test
    fun `an unmatched result does not block a later release`() = runTest {
        val uiModel = createUiModel()
        advanceUntilIdle()

        // Retorno sem compra correspondente (nenhum pagamento em curso): deve ser descartado.
        paymentResultSource.post(approvedResult("outra-referencia"))
        advanceUntilIdle()
        assertFalse(paymentResultSource.hasPendingResult())

        uiModel.onPayClick()
        advanceUntilIdle()
        uiModel.onScreenResume()

        assertFalse(uiModel.state.value.isPaymentInFlight)
    }

    private fun createUiModel() = CheckoutUiModel(
        eventId = "evt-1",
        getEvent = GetEventUseCase { Event(id = "evt-1", name = "Festival", priceInCents = 12_000) },
        startPayment = startPayment,
        purchaseRepository = purchaseRepository,
        paymentResultSource = paymentResultSource,
    )
}
