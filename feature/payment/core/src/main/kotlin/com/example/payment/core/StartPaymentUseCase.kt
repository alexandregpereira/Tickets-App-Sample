package com.example.payment.core

/**
 * Charges an order and **suspends until the outcome**.
 *
 * One call, one result: the caller doesn't need to know an external app takes over the screen along
 * the way, nor observe any channel to find out how it ended.
 *
 * It never throws and never leaves the call unanswered — a user backing out and a missing payment
 * app also come back as [PaymentResult.Failed].
 */
fun interface StartPaymentUseCase {

    suspend operator fun invoke(order: PaymentOrder): PaymentResult
}
