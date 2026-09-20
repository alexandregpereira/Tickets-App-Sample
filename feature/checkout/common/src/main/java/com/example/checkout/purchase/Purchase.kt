package com.example.checkout.purchase

/**
 * A record of a ticket purchase attempt and its outcome.
 *
 * @param reference the order's idempotency key. Generated once per purchase attempt, sent to the
 * payment provider and reused across retries — it is what keeps a resent action from becoming a
 * second charge. It also identifies the purchase within the app.
 */
internal data class Purchase(
    val reference: String,
    val eventId: String,
    val eventName: String,
    val quantity: Int,
    val unitPriceInCents: Long,
    val status: PurchaseStatus,
    val paymentOrderId: String? = null,
    val authorizationCode: String? = null,
    val acquirerCode: String? = null,
    val brand: String? = null,
    val maskedCard: String? = null,
    val terminal: String? = null,
    /** Payment method chosen by the cardholder on the terminal, known only after the transaction. */
    val paymentDescription: String? = null,
    val failureReason: String? = null,
) {
    val totalInCents: Long get() = unitPriceInCents * quantity
}

internal enum class PurchaseStatus {
    /** Payment started, awaiting the payment provider's response. */
    PENDING,
    APPROVED,
    DENIED,
    CANCELLED;

    /** A final outcome cannot be overwritten — the basis of the repository's idempotency. */
    val isTerminal: Boolean get() = this != PENDING
}
