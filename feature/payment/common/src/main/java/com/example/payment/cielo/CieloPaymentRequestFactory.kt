package com.example.payment.cielo

import com.example.payment.core.PaymentOrder

/**
 * Traduz um [PaymentOrder] genérico para a requisição da Cielo.
 *
 * É a fronteira entre o vocabulário do app e o da adquirente, e está separada do use case para
 * continuar coberta por teste de JVM agora que iniciar o pagamento envolve Activity.
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
