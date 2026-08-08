package com.example.checkout.purchase

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseRepositoryTest {

    private val repository = PurchaseRepository()

    @Test
    fun `starting twice with the same reference keeps a single purchase`() {
        val first = repository.start(pendingPurchase(quantity = 2))
        val second = repository.start(pendingPurchase(quantity = 5))

        assertSame(first, second)
        assertEquals(2, repository.find(REFERENCE)!!.quantity)
    }

    @Test
    fun `records an approved result`() {
        repository.start(pendingPurchase())

        val purchase = repository.recordResult(REFERENCE, approved())!!

        assertEquals(PurchaseStatus.APPROVED, purchase.status)
        assertEquals("140126", purchase.authorizationCode)
        assertEquals("order-1", purchase.paymentOrderId)
        assertEquals(24_000L, purchase.totalInCents)
        // A forma de pagamento só é conhecida depois: quem escolhe é o portador, no terminal.
        assertEquals("CREDITO A VISTA", purchase.paymentDescription)
    }

    @Test
    fun `maps a cancellation and a denial to distinct statuses`() {
        repository.start(pendingPurchase())
        assertEquals(
            PurchaseStatus.CANCELLED,
            repository.recordResult(REFERENCE, failed(PaymentError.CANCELLED_BY_USER))!!.status,
        )

        val other = PurchaseRepository()
        other.start(pendingPurchase())
        assertEquals(
            PurchaseStatus.DENIED,
            other.recordResult(REFERENCE, failed(PaymentError.PAYMENT))!!.status,
        )
    }

    @Test
    fun `does not overwrite a purchase that already has a final outcome`() {
        repository.start(pendingPurchase())
        repository.recordResult(REFERENCE, approved())

        // Entrega repetida da mesma Intent de resposta: não pode virar uma segunda cobrança.
        val reapplied = repository.recordResult(REFERENCE, failed(PaymentError.PAYMENT))!!

        assertEquals(PurchaseStatus.APPROVED, reapplied.status)
        assertEquals("140126", reapplied.authorizationCode)
        assertNull(reapplied.failureReason)
    }

    @Test
    fun `discards a pending purchase`() {
        repository.start(pendingPurchase())

        assertTrue(repository.discard(REFERENCE))

        assertNull(repository.find(REFERENCE))
    }

    @Test
    fun `refuses to discard a purchase that already has a final outcome`() {
        repository.start(pendingPurchase())
        repository.recordResult(REFERENCE, approved())

        // Descarte é para tentativa abandonada; uma venda registrada não pode sumir.
        assertFalse(repository.discard(REFERENCE))

        assertEquals(PurchaseStatus.APPROVED, repository.find(REFERENCE)!!.status)
    }

    @Test
    fun `discarding an unknown reference is a no-op`() {
        assertFalse(repository.discard("nao-existe"))
    }

    @Test
    fun `ignores a result for an unknown reference`() {
        assertNull(repository.recordResult("nao-existe", approved()))
    }

    private fun pendingPurchase(quantity: Int = 2) = Purchase(
        reference = REFERENCE,
        eventId = "evt-1",
        eventName = "Festival de Verão",
        quantity = quantity,
        unitPriceInCents = 12_000,
        status = PurchaseStatus.PENDING,
    )

    private fun approved() = PaymentResult.Approved(
        orderId = "order-1",
        reference = REFERENCE,
        paidAmountInCents = 24_000,
        authorizationCode = "140126",
        acquirerCode = "799871",
        brand = "Visa",
        maskedCard = "424242-4242",
        terminal = "69000007",
        paymentDescription = "CREDITO A VISTA",
    )

    private fun failed(error: PaymentError) =
        PaymentResult.Failed(error, reason = "motivo")

    private companion object {
        const val REFERENCE = "ref-1"
    }
}
