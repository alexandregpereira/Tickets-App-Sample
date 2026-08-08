package com.example.cielo.checkout

import com.example.cielo.MainDispatcherRule
import com.example.cielo.cielo.CieloCheckoutLauncher
import com.example.cielo.cielo.CieloCredentials
import com.example.cielo.cielo.CieloDeepLinkBuilder
import com.example.cielo.cielo.CieloResponseParser
import com.example.cielo.cielo.CieloResultBus
import com.example.cielo.cielo.JvmBase64Codec
import com.example.cielo.event.GetEventUseCase
import com.example.cielo.event.GetEventsUseCase
import com.example.cielo.purchase.PurchaseRepository
import com.example.cielo.purchase.PurchaseStatus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Cobre a corrida entre o retorno da Cielo e o `ON_RESUME` da tela.
 *
 * No Android real, `onNewIntent` (que publica o retorno) e `onResume` acontecem na mesma passagem
 * pela main thread, então `onScreenResume` roda **antes** de o coletor do bus processar a resposta.
 * Um [StandardTestDispatcher] reproduz essa ordem: nada é executado até `advanceUntilIdle`.
 */
class CheckoutUiModelResumeRaceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val codec = JvmBase64Codec()
    private val purchaseRepository = PurchaseRepository()
    private val resultBus = CieloResultBus()
    private val launchedUris = mutableListOf<String>()

    @Test
    fun `does not release the screen when a response is published but not processed yet`() =
        runTest {
            val uiModel = createUiModel()
            advanceUntilIdle() // carrega o evento
            uiModel.onPayClick()
            advanceUntilIdle()
            val reference = launchedUris.single().reference()

            resultBus.post(codec.encode(approvedOrderJson(reference).toByteArray()))
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
    fun `an unmatched response does not block a later release`() = runTest {
        val uiModel = createUiModel()
        advanceUntilIdle()

        // Retorno sem compra correspondente (nenhum pagamento em curso): deve ser descartado.
        resultBus.post(codec.encode(approvedOrderJson("outra-referencia").toByteArray()))
        advanceUntilIdle()
        assertFalse(resultBus.hasPendingResponse())

        uiModel.onPayClick()
        advanceUntilIdle()
        uiModel.onScreenResume()

        assertFalse(uiModel.state.value.isPaymentInFlight)
    }

    private fun createUiModel() = CheckoutUiModel(
        eventId = "evt-1",
        getEvent = GetEventUseCase(GetEventsUseCase()),
        startPayment = StartPaymentUseCase(
            deepLinkBuilder = CieloDeepLinkBuilder(codec),
            checkoutLauncher = CieloCheckoutLauncher { launchedUris += it },
            purchaseRepository = purchaseRepository,
            credentials = CieloCredentials.MOCK,
        ),
        purchaseRepository = purchaseRepository,
        responseParser = CieloResponseParser(codec),
        resultBus = resultBus,
    )

    private fun String.reference() = substringAfter("request=")
        .substringBefore("&urlCallback")
        .let { Json.parseToJsonElement(String(codec.decode(it))).jsonObject }
        .getValue("reference").jsonPrimitive.content

    private fun approvedOrderJson(reference: String) = """
        {
          "id": "order-1",
          "paidAmount": 12000,
          "reference": "$reference",
          "payments": [
            { "authCode": "140126", "paymentFields": { "statusCode": "1" } }
          ]
        }
    """.trimIndent()
}
