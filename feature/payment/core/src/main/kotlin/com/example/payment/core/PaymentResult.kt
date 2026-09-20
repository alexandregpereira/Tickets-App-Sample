package com.example.payment.core

/**
 * The outcome of a charge, already translated out of the acquirer's vocabulary.
 */
sealed interface PaymentResult {

    data class Approved(
        /** The order's identifier at the acquirer. */
        val orderId: String,
        /** The idempotency reference echoed back, when the acquirer returns it. */
        val reference: String?,
        val paidAmountInCents: Long,
        val authorizationCode: String?,
        val acquirerCode: String?,
        val brand: String?,
        val maskedCard: String?,
        val terminal: String?,
        /**
         * The payment method the cardholder actually chose — e.g. "CREDITO A VISTA",
         * "PIX PAGAMENTO". The caller starting the payment doesn't set it, so this is the only
         * place it can come from.
         */
        val paymentDescription: String?,
    ) : PaymentResult

    data class Failed(val error: PaymentError, val reason: String) : PaymentResult
}

enum class PaymentError {
    CANCELLED_BY_USER,
    GENERIC,
    PAYMENT,
    AUTHENTICATION,

    /** There is no payment app installed to handle the charge. */
    APP_NOT_FOUND,

    /** The response arrived, but could not be interpreted. */
    INVALID_RESPONSE,

    /**
     * The user left the flow before any outcome — they came back without paying or cancelling.
     *
     * Unlike [CANCELLED_BY_USER], which is a refusal coming from the acquirer: here the charge was
     * never attempted, so there is nothing to record and no receipt to show.
     */
    ABANDONED,
}
