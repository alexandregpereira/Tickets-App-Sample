package com.example.payment.cielo

import com.example.payment.core.PaymentOrder

/**
 * Translates a generic [PaymentOrder] into Cielo's request.
 *
 * It is the boundary between the app's vocabulary and the acquirer's, and it is kept separate from
 * the use case so it stays covered by JVM tests now that starting a payment involves an Activity.
 */
internal class CieloPaymentRequestFactory(
    private val credentials: CieloCredentials,
) {

    fun create(order: PaymentOrder) = CieloPaymentRequest(
        accessToken = credentials.accessToken,
        clientId = credentials.clientId,
        reference = order.reference,
        items = order.items.map { item ->
            CieloPaymentItem(
                name = item.name,
                quantity = item.quantity,
                sku = item.sku,
                unitPrice = item.unitPriceInCents,
            )
        },
        value = order.totalInCents.toString(),
    )
}
