package com.example.payment.cielo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CieloDeepLinkBuilderTest {

    private val codec = JvmBase64Codec()
    private val builder = CieloDeepLinkBuilder(codec)

    @Test
    fun `builds uri with cielo payment scheme and response callback`() {
        val uri = builder.buildPaymentUri(request())

        assertTrue(uri.startsWith("lio://payment?request="))
        assertTrue(uri.endsWith("&urlCallback=order://response"))
    }

    @Test
    fun `encodes the request as base64 json with credentials and reference`() {
        val uri = builder.buildPaymentUri(
            request(reference = "ref-123", clientId = "client-1", accessToken = "token-1")
        )

        val payload = uri.decodedRequest()
        assertEquals("ref-123", payload["reference"]!!.jsonPrimitive.content)
        assertEquals("client-1", payload["clientID"]!!.jsonPrimitive.content)
        assertEquals("token-1", payload["accessToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `sends total value and unit price in cents`() {
        val uri = builder.buildPaymentUri(
            request(quantity = 3, unitPriceInCents = 12_000, totalInCents = 36_000)
        )

        val payload = uri.decodedRequest()
        assertEquals("36000", payload["value"]!!.jsonPrimitive.content)

        val item = payload["items"]!!.jsonArray.single().jsonObject
        assertEquals("12000", item["unitPrice"]!!.jsonPrimitive.content)
        assertEquals("3", item["quantity"]!!.jsonPrimitive.content)
        assertEquals("unidade", item["unitOfMeasure"]!!.jsonPrimitive.content)
    }

    @Test
    fun `omits the payment code so the shopper chooses on the cielo screen`() {
        val uri = builder.buildPaymentUri(request())

        // `paymentCode` is optional per the documentation: without it, Cielo Smart shows the
        // payment method selection on the terminal itself.
        assertNull(uri.decodedRequest()["paymentCode"])
    }

    private fun String.decodedRequest() = substringAfter("request=")
        .substringBefore("&urlCallback")
        .let { Json.parseToJsonElement(String(codec.decode(it))).jsonObject }

    private fun request(
        reference: String = "ref",
        clientId: String = "client",
        accessToken: String = "token",
        quantity: Int = 1,
        unitPriceInCents: Long = 1_000,
        totalInCents: Long = 1_000,
    ) = CieloPaymentRequest(
        accessToken = accessToken,
        clientId = clientId,
        reference = reference,
        items = listOf(
            CieloPaymentItem(
                name = "Show - Ingresso",
                quantity = quantity,
                sku = "evt-1",
                unitPrice = unitPriceInCents,
            )
        ),
        value = totalInCents.toString(),
    )
}
