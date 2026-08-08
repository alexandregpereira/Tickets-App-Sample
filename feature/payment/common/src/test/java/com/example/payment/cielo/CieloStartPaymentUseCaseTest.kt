package com.example.payment.cielo

import android.content.ActivityNotFoundException
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentItem
import com.example.payment.core.PaymentOrder
import com.example.payment.core.StartPaymentResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Traduz um [PaymentOrder] genérico para a requisição da Cielo — a fronteira entre o contrato do app
 * e o protocolo da adquirente.
 */
class CieloStartPaymentUseCaseTest {

    private val codec = JvmBase64Codec()
    private val launcher = RecordingCheckoutLauncher()
    private val useCase = CieloStartPaymentUseCase(
        deepLinkBuilder = CieloDeepLinkBuilder(codec),
        checkoutLauncher = launcher,
        credentials = CieloCredentials.MOCK,
    )

    @Test
    fun `maps a payment order onto the cielo request`() = runTest {
        val result = useCase(
            PaymentOrder(
                reference = "ref-123",
                totalInCents = 24_000,
                items = listOf(
                    PaymentItem(
                        sku = "evt-1",
                        name = "Festival de Verão - Ingresso",
                        quantity = 2,
                        unitPriceInCents = 12_000,
                    )
                ),
            )
        )

        assertEquals(StartPaymentResult.Launched, result)

        val request = launcher.uris.single().decodedRequest()
        assertEquals("ref-123", request["reference"]!!.jsonPrimitive.content)
        assertEquals("24000", request["value"]!!.jsonPrimitive.content)
        assertEquals(CieloCredentials.MOCK.clientId, request["clientID"]!!.jsonPrimitive.content)
        assertEquals(CieloCredentials.MOCK.accessToken, request["accessToken"]!!.jsonPrimitive.content)

        val item = request["items"]!!.jsonArray.single().jsonObject
        assertEquals("evt-1", item["sku"]!!.jsonPrimitive.content)
        assertEquals("Festival de Verão - Ingresso", item["name"]!!.jsonPrimitive.content)
        assertEquals("2", item["quantity"]!!.jsonPrimitive.content)
        assertEquals("12000", item["unitPrice"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reports app not found when the cielo app is missing`() = runTest {
        launcher.failNextLaunch = true

        val result = useCase(PaymentOrder("ref", 1_000, emptyList()))

        val failed = result as StartPaymentResult.Failed
        assertEquals(PaymentError.APP_NOT_FOUND, failed.error)
        assertTrue(failed.reason.contains("Cielo Smart"))
    }

    private fun String.decodedRequest() = substringAfter("request=")
        .substringBefore("&urlCallback")
        .let { Json.parseToJsonElement(String(codec.decode(it))).jsonObject }

    private class RecordingCheckoutLauncher : CieloCheckoutLauncher {
        val uris = mutableListOf<String>()
        var failNextLaunch = false

        override fun launch(uri: String) {
            if (failNextLaunch) {
                failNextLaunch = false
                throw ActivityNotFoundException("Cielo Smart não instalada")
            }
            uris += uri
        }
    }
}
