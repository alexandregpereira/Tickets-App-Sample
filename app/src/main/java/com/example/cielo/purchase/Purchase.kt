package com.example.cielo.purchase

/**
 * Registro de uma tentativa de compra de ingressos e seu desfecho.
 *
 * @param reference chave de idempotência do pedido. É gerada uma única vez por tentativa de compra,
 * enviada à Cielo no campo `reference` e reutilizada em retentativas — é o que garante que um
 * reenvio da ação não vire uma segunda cobrança. Também serve de identificador da compra no app.
 */
data class Purchase(
    val reference: String,
    val eventId: String,
    val eventName: String,
    val quantity: Int,
    val unitPriceInCents: Long,
    val status: PurchaseStatus,
    val cieloOrderId: String? = null,
    val authCode: String? = null,
    val cieloCode: String? = null,
    val brand: String? = null,
    val maskedCard: String? = null,
    val terminal: String? = null,
    /** Forma de pagamento escolhida pelo portador na Cielo Smart, conhecida só após a transação. */
    val paymentDescription: String? = null,
    val failureReason: String? = null,
) {
    val totalInCents: Long get() = unitPriceInCents * quantity
}

enum class PurchaseStatus {
    /** Pagamento iniciado, aguardando o retorno da Cielo. */
    PENDING,
    APPROVED,
    DENIED,
    CANCELLED;

    /** Um desfecho definitivo não pode ser sobrescrito — base da idempotência do repositório. */
    val isTerminal: Boolean get() = this != PENDING
}
