package com.example.cielo.purchase

import com.example.cielo.cielo.CieloPaymentError
import com.example.cielo.cielo.CieloPaymentResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Guarda as compras em memória, indexadas pela `reference` (chave de idempotência).
 *
 * Última linha de defesa contra cobrança/registro duplicado: [start] nunca cria duas compras para a
 * mesma referência e [recordResult] ignora escritas sobre uma compra que já tem desfecho definitivo.
 * Isso importa porque a resposta da Cielo chega como uma nova Intent, que o Android pode entregar
 * mais de uma vez (por exemplo, se a Activity for recriada com a mesma Intent).
 *
 * Trade-off assumido: sem persistência, as compras somem se o processo morrer. Ver README.
 */
class PurchaseRepository {

    private val purchases = ConcurrentHashMap<String, Purchase>()

    /**
     * Registra a compra como [PurchaseStatus.PENDING]. Se a referência já existir — retentativa de
     * um pagamento que falhou — devolve o registro existente sem sobrescrevê-lo.
     */
    fun start(purchase: Purchase): Purchase =
        purchases.putIfAbsent(purchase.reference, purchase) ?: purchase

    /**
     * Aplica o resultado da Cielo à compra.
     *
     * @return a compra atualizada, ou `null` se a referência for desconhecida.
     * Se a compra já estiver em estado terminal, devolve-a inalterada.
     */
    fun recordResult(reference: String, result: CieloPaymentResult): Purchase? {
        val current = purchases[reference] ?: return null
        if (current.status.isTerminal) return current

        val updated = when (result) {
            is CieloPaymentResult.Approved -> current.copy(
                status = PurchaseStatus.APPROVED,
                cieloOrderId = result.orderId,
                authCode = result.authCode,
                cieloCode = result.cieloCode,
                brand = result.brand,
                maskedCard = result.maskedCard,
                terminal = result.terminal,
            )

            is CieloPaymentResult.Failed -> current.copy(
                status = result.error.toPurchaseStatus(),
                failureReason = result.reason,
            )
        }
        purchases[reference] = updated
        return updated
    }

    fun find(reference: String): Purchase? = purchases[reference]

    private fun CieloPaymentError.toPurchaseStatus(): PurchaseStatus = when (this) {
        CieloPaymentError.CANCELLED_BY_USER -> PurchaseStatus.CANCELLED
        else -> PurchaseStatus.DENIED
    }
}
