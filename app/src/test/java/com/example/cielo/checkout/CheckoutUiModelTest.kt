package com.example.cielo.checkout

import android.content.ActivityNotFoundException
import app.cash.turbine.test
import com.example.cielo.MainDispatcherRule
import com.example.cielo.cielo.CieloCheckoutLauncher
import com.example.cielo.cielo.CieloCredentials
import com.example.cielo.cielo.CieloDeepLinkBuilder
import com.example.cielo.cielo.CieloPaymentCode
import com.example.cielo.cielo.CieloResponseParser
import com.example.cielo.cielo.CieloResultBus
import com.example.cielo.cielo.JvmBase64Codec
import com.example.cielo.event.GetEventUseCase
import com.example.cielo.event.GetEventsUseCase
import com.example.cielo.purchase.PurchaseRepository
import com.example.cielo.purchase.PurchaseStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CheckoutUiModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val codec = JvmBase64Codec()
    private val purchaseRepository = PurchaseRepository()
    private val resultBus = CieloResultBus()
    private val launcher = RecordingCheckoutLauncher()

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
    fun `opens the cielo deep link with the selected quantity and payment code`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onIncreaseQuantityClick()
        uiModel.onPaymentCodeSelect(CieloPaymentCode.PIX)

        uiModel.onPayClick()

        val request = launcher.uris.single().decodedRequest()
        assertEquals("24000", request["value"]!!.jsonPrimitive.content)
        assertEquals("PIX", request["paymentCode"]!!.jsonPrimitive.content)
        assertTrue(uiModel.state.value.isPaymentInFlight)
        assertFalse(uiModel.state.value.canPay)
    }

    @Test
    fun `registers the purchase as pending before opening the checkout`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()

        val reference = launcher.uris.single().reference()
        val purchase = purchaseRepository.find(reference)
        assertNotNull(purchase)
        assertEquals(PurchaseStatus.PENDING, purchase!!.status)
    }

    @Test
    fun `ignores a second pay click while a payment is in flight`() = runTest {
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()
        uiModel.onPayClick()
        uiModel.onPayClick()

        assertEquals(1, launcher.uris.size)
    }

    @Test
    fun `reuses the same reference when retrying after a launch failure`() = runTest {
        launcher.failNextLaunch = true
        val uiModel = createUiModel().alsoLoaded()

        uiModel.onPayClick()
        assertFalse(uiModel.state.value.isPaymentInFlight)
        assertNotNull(uiModel.state.value.errorMessage)

        uiModel.onPayClick()

        assertEquals(2, launcher.attemptedUris.size)
        val references = launcher.attemptedUris.map { it.reference() }
        assertEquals(references[0], references[1])
        // Uma única compra registrada, apesar das duas tentativas.
        assertEquals(1, purchaseRepository.referencesOf(references).size)
    }

    @Test
    fun `records the approval and navigates to the receipt`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = launcher.uris.single().reference()

        uiModel.actions.test {
            resultBus.post(codec.encode(approvedOrderJson(reference).toByteArray()))

            assertEquals(CheckoutUiAction.NavigateToReceipt(reference), awaitItem())
        }

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.APPROVED, purchase.status)
        assertEquals("140126", purchase.authCode)
        assertFalse(uiModel.state.value.isPaymentInFlight)
    }

    @Test
    fun `records a cancellation and still navigates to the receipt`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = launcher.uris.single().reference()

        uiModel.actions.test {
            resultBus.post(
                codec.encode("""{"code":1,"reason":"CANCELADO PELO USUÁRIO"}""".toByteArray())
            )

            assertEquals(CheckoutUiAction.NavigateToReceipt(reference), awaitItem())
        }

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.CANCELLED, purchase.status)
        assertEquals("CANCELADO PELO USUÁRIO", purchase.failureReason)
    }

    @Test
    fun `a repeated response does not change an already recorded purchase`() = runTest {
        val uiModel = createUiModel().alsoLoaded()
        uiModel.onPayClick()
        val reference = launcher.uris.single().reference()
        val approved = codec.encode(approvedOrderJson(reference).toByteArray())

        resultBus.post(approved)
        // O Android pode reentregar a mesma Intent de resposta.
        resultBus.post(approved)
        resultBus.post(codec.encode("""{"code":3,"reason":"Erro no pagamento"}""".toByteArray()))

        val purchase = purchaseRepository.find(reference)!!
        assertEquals(PurchaseStatus.APPROVED, purchase.status)
        assertNull(purchase.failureReason)
    }

    private fun createUiModel(eventId: String = "evt-1") = CheckoutUiModel(
        eventId = eventId,
        getEvent = GetEventUseCase(GetEventsUseCase()),
        startPayment = StartPaymentUseCase(
            deepLinkBuilder = CieloDeepLinkBuilder(codec),
            checkoutLauncher = launcher,
            purchaseRepository = purchaseRepository,
            credentials = CieloCredentials.MOCK,
        ),
        purchaseRepository = purchaseRepository,
        responseParser = CieloResponseParser(codec),
        resultBus = resultBus,
    )

    /** Espera o carregamento inicial do evento terminar. */
    private suspend fun CheckoutUiModel.alsoLoaded(): CheckoutUiModel = apply {
        state.test {
            skipItems(1)
            awaitItem()
        }
    }

    private fun PurchaseRepository.referencesOf(references: List<String>) =
        references.distinct().mapNotNull { find(it) }

    private fun String.decodedRequest() = substringAfter("request=")
        .substringBefore("&urlCallback")
        .let { Json.parseToJsonElement(String(codec.decode(it))).jsonObject }

    private fun String.reference() = decodedRequest()["reference"]!!.jsonPrimitive.content

    private fun approvedOrderJson(reference: String) = """
        {
          "id": "order-1",
          "paidAmount": 12000,
          "reference": "$reference",
          "status": "ENTERED",
          "payments": [
            {
              "authCode": "140126",
              "cieloCode": "799871",
              "brand": "Visa",
              "mask": "424242-4242",
              "terminal": "69000007",
              "paymentFields": { "statusCode": "1", "productName": "CREDITO A VISTA - I" }
            }
          ]
        }
    """.trimIndent()

    private class RecordingCheckoutLauncher : CieloCheckoutLauncher {
        /** Toda tentativa de abrir o checkout, inclusive as que falharam. */
        val attemptedUris = mutableListOf<String>()

        /** Somente as que abriram o app da Cielo com sucesso. */
        val uris = mutableListOf<String>()

        var failNextLaunch = false

        override fun launch(uri: String) {
            attemptedUris += uri
            if (failNextLaunch) {
                failNextLaunch = false
                throw ActivityNotFoundException("Cielo Smart não instalada")
            }
            uris += uri
        }
    }
}
