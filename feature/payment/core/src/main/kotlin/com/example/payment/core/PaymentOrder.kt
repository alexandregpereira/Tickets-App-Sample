package com.example.payment.core

/**
 * Pedido a ser cobrado.
 *
 * Fala apenas de dinheiro e de identificadores: nada de ingressos, eventos ou compras. É esse
 * vocabulário reduzido que permite ao app de ingressos e ao meio de pagamento evoluírem separados.
 *
 * @param reference chave de idempotência do pedido. Quem chama gera uma vez e **reutiliza** em
 * retentativas, para que a adquirente enxergue sempre o mesmo pedido lógico e não cobre duas vezes.
 * @param totalInCents valor total em centavos inteiros — pagamento não usa ponto flutuante.
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
