package com.example.checkout.purchase

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps purchases in memory, indexed by `reference` (the idempotency key).
 *
 * The last line of defense against a duplicate charge or record: [start] never creates two purchases
 * for the same reference, and [recordResult] ignores writes over a purchase that already has a final
 * outcome. That matters because the outcome arrives as a new Intent, which Android may deliver more
 * than once (for instance, if the Activity is recreated with the same Intent).
 *
 * Accepted trade-off: with no persistence, purchases vanish if the process dies. See the README.
 */
internal class PurchaseRepository {

    private val purchases = ConcurrentHashMap<String, Purchase>()

    /**
     * Records the purchase as [PurchaseStatus.PENDING].
     *
     * Never overwrites an existing record: each payment attempt has its own `reference`, so a
     * collision could only come from a duplicate call within the same attempt.
     */
    fun start(purchase: Purchase): Purchase =
        purchases.putIfAbsent(purchase.reference, purchase) ?: purchase

    /**
     * Removes a still-pending purchase when the attempt is abandoned before any charge — the user
     * left the payment app without finishing, or the payment never even opened.
     *
     * Purchases with a final outcome are **never** removed: they are the record of the sale.
     *
     * @return `true` if there was a pending purchase and it was discarded.
     */
    fun discard(reference: String): Boolean {
        val current = purchases[reference] ?: return false
        if (current.status.isTerminal) return false
        return purchases.remove(reference, current)
    }

    /**
     * Applies the payment outcome to the purchase.
     *
     * @return the updated purchase, or `null` if the reference is unknown.
     * If the purchase is already in a terminal state, returns it unchanged.
     */
    fun recordResult(reference: String, result: PaymentResult): Purchase? {
        val current = purchases[reference] ?: return null
        if (current.status.isTerminal) return current

        val updated = when (result) {
            is PaymentResult.Approved -> current.copy(
                status = PurchaseStatus.APPROVED,
                paymentOrderId = result.orderId,
                authorizationCode = result.authorizationCode,
                acquirerCode = result.acquirerCode,
                brand = result.brand,
                maskedCard = result.maskedCard,
                terminal = result.terminal,
                paymentDescription = result.paymentDescription,
            )

            is PaymentResult.Failed -> current.copy(
                status = result.error.toPurchaseStatus(),
                failureReason = result.reason,
            )
        }
        purchases[reference] = updated
        return updated
    }

    fun find(reference: String): Purchase? = purchases[reference]

    private fun PaymentError.toPurchaseStatus(): PurchaseStatus = when (this) {
        PaymentError.CANCELLED_BY_USER -> PurchaseStatus.CANCELLED
        else -> PurchaseStatus.DENIED
    }
}
