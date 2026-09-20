package com.example.payment.core

/**
 * An order to be charged.
 *
 * It speaks only of money and identifiers: no tickets, events or purchases. That reduced vocabulary
 * is what lets the ticketing app and the payment provider evolve independently.
 *
 * @param reference the order's idempotency key. The caller generates it once and **reuses** it on
 * retries, so the acquirer always sees the same logical order and doesn't charge twice.
 * @param totalInCents total amount in integer cents — payments don't use floating point.
 */
data class PaymentOrder(
    val reference: String,
    val totalInCents: Long,
    val items: List<PaymentItem>,
)

data class PaymentItem(
    val sku: String,
    val name: String,
    val quantity: Int,
    val unitPriceInCents: Long,
)
