package com.example.checkout.purchase

/**
 * Registro de uma tentativa de compra de ingressos e seu desfecho.
 *
 * @param reference chave de idempotência do pedido. É gerada uma única vez por tentativa de compra,
 * enviada ao meio de pagamento e reutilizada em retentativas — é o que garante que um
 * reenvio da ação não vire uma segunda cobrança. Também serve de identificador da compra no app.
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
    /** Forma de pagamento escolhida pelo portador no terminal, conhecida só após a transação. */
    val paymentDescription: String? = null,
    val failureReason: String? = null,
) {
    val totalInCents: Long get() = unitPriceInCents * quantity
}

internal enum class PurchaseStatus {
    /** Pagamento iniciado, aguardando o retorno do meio de pagamento. */
    PENDING,
    APPROVED,
    DENIED,
    CANCELLED;

    /** Um desfecho definitivo não pode ser sobrescrito — base da idempotência do repositório. */
    val isTerminal: Boolean get() = this != PENDING
}
