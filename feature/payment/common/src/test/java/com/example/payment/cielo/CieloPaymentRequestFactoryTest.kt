package com.example.payment.cielo

import com.example.payment.core.PaymentItem
import com.example.payment.core.PaymentOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** A fronteira entre o vocabulário do app e o da adquirente. */
class CieloPaymentRequestFactoryTest {

    private val factory = CieloPaymentRequestFactory(CieloCredentials.MOCK)

    @Test
    fun `maps a payment order onto the cielo request`() {
        val request = factory.create(
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

        assertEquals("ref-123", request.reference)
        // A Cielo espera o total em centavos, como string.
        assertEquals("24000", request.value)
        assertEquals(CieloCredentials.MOCK.clientId, request.clientId)
        assertEquals(CieloCredentials.MOCK.accessToken, request.accessToken)

        val item = request.items.single()
        assertEquals("evt-1", item.sku)
        assertEquals("Festival de Verão - Ingresso", item.name)
        assertEquals(2, item.quantity)
        assertEquals(12_000L, item.unitPrice)
        assertEquals("unidade", item.unitOfMeasure)
    }
}
