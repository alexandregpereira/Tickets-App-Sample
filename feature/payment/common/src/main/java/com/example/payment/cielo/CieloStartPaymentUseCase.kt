package com.example.payment.cielo

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase

/**
 * Charges an order by opening [PaymentActivity] through the Activity Result API and suspending
 * until the outcome.
 *
 * The whole conversation with Cielo Smart fits in one call: it builds the request, turns it into a
 * deep link, hands it to the bridge screen and waits for the result — which [PaymentResultLauncher]
 * delivers even if the host Activity is recreated along the way.
 */
internal class CieloStartPaymentUseCase(
    private val requestFactory: CieloPaymentRequestFactory,
    private val deepLinkBuilder: CieloDeepLinkBuilder,
    private val resultLauncher: PaymentResultLauncher,
) : StartPaymentUseCase {

    override suspend fun invoke(order: PaymentOrder): PaymentResult {
        val deepLink = deepLinkBuilder.buildPaymentUri(requestFactory.create(order))
        // A key per order: two payments never coexist, but this way a pending registration is never
        // confused with another purchase's — and it is what finds the result again after a
        // recreation.
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
