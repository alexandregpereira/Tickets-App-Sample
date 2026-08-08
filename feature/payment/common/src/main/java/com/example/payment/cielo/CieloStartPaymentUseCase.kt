package com.example.payment.cielo

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase

/**
 * Cobra um pedido abrindo a [PaymentActivity] pela Activity Result API e suspendendo até o desfecho.
 *
 * Toda a conversa com a Cielo Smart cabe numa chamada: monta a requisição, vira deep link, entrega à
 * tela-ponte e espera o resultado — que o [PaymentResultLauncher] devolve mesmo que a Activity
 * hospedeira seja recriada no meio do caminho.
 */
internal class CieloStartPaymentUseCase(
    private val requestFactory: CieloPaymentRequestFactory,
    private val deepLinkBuilder: CieloDeepLinkBuilder,
    private val resultLauncher: PaymentResultLauncher,
) : StartPaymentUseCase {

    override suspend fun invoke(order: PaymentOrder): PaymentResult {
        val deepLink = deepLinkBuilder.buildPaymentUri(requestFactory.create(order))
        // Chave por pedido: dois pagamentos nunca coexistem, mas assim um registro pendente jamais é
        // confundido com o de outra compra — e é ela que reencontra o resultado após uma recriação.
        return resultLauncher.launch("$REGISTRY_KEY_PREFIX${order.reference}", deepLink)
            ?: PaymentResult.Failed(
                error = PaymentError.GENERIC,
                reason = "Não foi possível abrir o pagamento agora. Tente novamente.",
            )
    }

    private companion object {
        const val REGISTRY_KEY_PREFIX = "cielo_payment_"
    }
}
