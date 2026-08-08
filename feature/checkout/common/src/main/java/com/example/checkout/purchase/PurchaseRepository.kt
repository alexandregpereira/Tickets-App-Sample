package com.example.checkout.purchase

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Guarda as compras em memória, indexadas pela `reference` (chave de idempotência).
 *
 * Última linha de defesa contra cobrança/registro duplicado: [start] nunca cria duas compras para a
 * mesma referência e [recordResult] ignora escritas sobre uma compra que já tem desfecho definitivo.
 * Isso importa porque o desfecho chega como uma nova Intent, que o Android pode entregar
 * mais de uma vez (por exemplo, se a Activity for recriada com a mesma Intent).
 *
 * Trade-off assumido: sem persistência, as compras somem se o processo morrer. Ver README.
 */
internal class PurchaseRepository {

    private val purchases = ConcurrentHashMap<String, Purchase>()

    /**
     * Registra a compra como [PurchaseStatus.PENDING].
     *
     * Nunca sobrescreve um registro existente: cada tentativa de pagamento tem a sua própria
     * `reference`, então uma colisão só aconteceria por chamada duplicada da mesma tentativa.
     */
    fun start(purchase: Purchase): Purchase =
        purchases.putIfAbsent(purchase.reference, purchase) ?: purchase

    /**
     * Remove uma compra ainda pendente, quando a tentativa é abandonada antes de qualquer cobrança
     * — o usuário saiu do app de pagamento sem concluir, ou o pagamento nem chegou a abrir.
     *
     * Compras com desfecho definitivo **nunca** são removidas: elas são o registro da venda.
     *
     * @return `true` se havia uma compra pendente e ela foi descartada.
     */
    fun discard(reference: String): Boolean {
        val current = purchases[reference] ?: return false
        if (current.status.isTerminal) return false
        return purchases.remove(reference, current)
    }

    /**
     * Aplica o desfecho do pagamento à compra.
     *
     * @return a compra atualizada, ou `null` se a referência for desconhecida.
     * Se a compra já estiver em estado terminal, devolve-a inalterada.
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
