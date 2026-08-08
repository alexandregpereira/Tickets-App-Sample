package com.example.payment.cielo

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Cobra um pedido abrindo a [PaymentActivity] pela Activity Result API e suspendendo até o desfecho.
 *
 * Toda a conversa com a Cielo Smart cabe numa chamada: monta a requisição, vira deep link, entrega à
 * tela-ponte e espera o resultado voltar pelo `ActivityResultRegistry`.
 */
internal class CieloStartPaymentUseCase(
    private val requestFactory: CieloPaymentRequestFactory,
    private val deepLinkBuilder: CieloDeepLinkBuilder,
    private val contract: CieloPaymentContract,
    private val activityProvider: CurrentActivityProvider,
) : StartPaymentUseCase {

    override suspend fun invoke(order: PaymentOrder): PaymentResult {
        val deepLink = deepLinkBuilder.buildPaymentUri(requestFactory.create(order))
        val activity = activityProvider.current() ?: return PaymentResult.Failed(
            error = PaymentError.GENERIC,
            reason = "Não foi possível abrir o pagamento agora. Tente novamente.",
        )

        return suspendCancellableCoroutine { continuation ->
            // Chave por pedido: dois pagamentos nunca coexistem, mas assim um registro pendente
            // jamais é confundido com o de outra compra.
            val launcher = activity.activityResultRegistry.register(
                "$REGISTRY_KEY_PREFIX${order.reference}",
                contract,
            ) { result ->
                continuation.resume(result)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(deepLink)
        }
    }

    private companion object {
        const val REGISTRY_KEY_PREFIX = "cielo_payment_"
    }
}
