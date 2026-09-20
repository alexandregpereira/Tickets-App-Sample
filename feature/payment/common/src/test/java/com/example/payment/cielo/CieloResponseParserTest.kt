package com.example.payment.cielo

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CieloResponseParserTest {

    private val codec = JvmBase64Codec()
    private val parser = CieloResponseParser(codec)

    @Test
    fun `parses an approved order`() {
        val result = parser.parse(encode(APPROVED_ORDER_JSON))

        val approved = result as PaymentResult.Approved
        assertEquals("ba583f85-9252-48b5-8fed-12719ff058b9", approved.orderId)
        assertEquals("ref-abc", approved.reference)
        assertEquals(1450L, approved.paidAmountInCents)
        assertEquals("140126", approved.authorizationCode)
        assertEquals("799871", approved.acquirerCode)
        assertEquals("Visa", approved.brand)
        assertEquals("424242-4242", approved.maskedCard)
        assertEquals("69000007", approved.terminal)
    }

    @Test
    fun `describes the payment method chosen on the terminal`() {
        val approved = parser.parse(encode(APPROVED_ORDER_JSON)) as PaymentResult.Approved

        assertEquals("CREDITO A VISTA", approved.paymentDescription)
    }

    @Test
    fun `does not repeat the product name when the secondary already contains it`() {
        // The format returned by the Cielo Emulator.
        val json = APPROVED_ORDER_JSON
            .replace("\"secondaryProductName\": \"A VISTA\"", "\"secondaryProductName\": \"PIX PAGAMENTO\"")
            .replace("\"primaryProductName\": \"CREDITO\"", "\"primaryProductName\": \"PIX\"")

        val approved = parser.parse(encode(json)) as PaymentResult.Approved

        assertEquals("PIX PAGAMENTO", approved.paymentDescription)
    }

    @Test
    fun `falls back to productName when the product names are missing`() {
        val json = APPROVED_ORDER_JSON
            .replace("\"primaryProductName\": \"CREDITO\",", "")
            .replace("\"secondaryProductName\": \"A VISTA\",", "")

        val approved = parser.parse(encode(json)) as PaymentResult.Approved

        assertEquals("CREDITO A VISTA - I", approved.paymentDescription)
    }

    @Test
    fun `has no payment description when the terminal sends none`() {
        val json = APPROVED_ORDER_JSON
            .replace("\"primaryProductName\": \"CREDITO\",", "")
            .replace("\"secondaryProductName\": \"A VISTA\",", "")
            .replace("\"productName\": \"CREDITO A VISTA - I\",", "")

        val approved = parser.parse(encode(json)) as PaymentResult.Approved

        assertEquals(null, approved.paymentDescription)
    }

    @Test
    fun `maps each cielo error code`() {
        val expected = mapOf(
            1 to PaymentError.CANCELLED_BY_USER,
            2 to PaymentError.GENERIC,
            3 to PaymentError.PAYMENT,
            4 to PaymentError.AUTHENTICATION,
        )

        expected.forEach { (code, error) ->
            val result = parser.parse(encode("""{"code":$code,"reason":"motivo $code"}"""))

            val failed = result as PaymentResult.Failed
            assertEquals(error, failed.error)
            assertEquals("motivo $code", failed.reason)
        }
    }

    @Test
    fun `treats an unknown error code as generic`() {
        val result = parser.parse(encode("""{"code":99,"reason":"desconhecido"}"""))

        assertEquals(PaymentError.GENERIC, (result as PaymentResult.Failed).error)
    }

    @Test
    fun `treats statusCode 2 as a cancelled transaction`() {
        val json = APPROVED_ORDER_JSON.replace("\"statusCode\": \"1\"", "\"statusCode\": \"2\"")

        val result = parser.parse(encode(json))

        assertEquals(
            PaymentError.CANCELLED_BY_USER,
            (result as PaymentResult.Failed).error,
        )
    }

    @Test
    fun `fails when the order has no transaction`() {
        val result = parser.parse(encode("""{"id":"1","paidAmount":0,"payments":[]}"""))

        assertEquals(PaymentError.PAYMENT, (result as PaymentResult.Failed).error)
    }

    @Test
    fun `fails without throwing on a missing, malformed or non-json response`() {
        val invalidInputs = listOf(null, "", "not-base64!!", encode("isso nao e json"))

        invalidInputs.forEach { input ->
            val result = parser.parse(input)

            assertTrue("esperava falha para: $input", result is PaymentResult.Failed)
            assertEquals(
                PaymentError.INVALID_RESPONSE,
                (result as PaymentResult.Failed).error,
            )
        }
    }

    private fun encode(json: String) = codec.encode(json.toByteArray())

    private companion object {
        /** An excerpt of the real payload documented at `docs/recuperando-dados`. */
        val APPROVED_ORDER_JSON = """
            {
              "createdAt": "Jun 8, 2018 1:51:58 PM",
              "id": "ba583f85-9252-48b5-8fed-12719ff058b9",
              "items": [
                { "name": "cocacola", "quantity": 2, "sku": "1234", "unitPrice": 250 }
              ],
              "paidAmount": 1450,
              "payments": [
                {
                  "amount": 1450,
                  "authCode": "140126",
                  "brand": "Visa",
                  "cieloCode": "799871",
                  "installments": 0,
                  "mask": "424242-4242",
                  "merchantCode": "0000000000000003",
                  "paymentFields": {
                    "statusCode": "1",
                    "primaryProductName": "CREDITO",
                    "secondaryProductName": "A VISTA",
                    "productName": "CREDITO A VISTA - I",
                    "merchantName": "POSTO ABC"
                  },
                  "terminal": "69000007"
                }
              ],
              "pendingAmount": 0,
              "price": 1450,
              "reference": "ref-abc",
              "status": "ENTERED",
              "type": "PAYMENT"
            }
        """.trimIndent()
    }
}
