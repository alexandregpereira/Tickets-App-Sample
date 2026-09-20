package com.example.checkout

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase

/**
 * A stand-in for the `payment:core` contract.
 *
 * The checkout is tested without a trace of an acquirer: what matters is *which order* was sent and
 * *how* the screen reacts to the outcome — the deep link format is `feature:payment:common`'s
 * business.
 */
internal class FakeStartPaymentUseCase : StartPaymentUseCase {

    /** Every order received, in order. */
    val orders = mutableListOf<PaymentOrder>()

    /** The outcome returned on the next call. */
    var nextResult: PaymentResult = approvedResult()

    override suspend fun invoke(order: PaymentOrder): PaymentResult {
        orders += order
        return nextResult.let { result ->
            if (result is PaymentResult.Approved) result.copy(reference = order.reference) else result
        }
    }
}

internal fun approvedResult(reference: String? = null) = PaymentResult.Approved(
    orderId = "order-1",
    reference = reference,
    paidAmountInCents = 12_000,
    authorizationCode = "140126",
    acquirerCode = "799871",
    brand = "Visa",
    maskedCard = "424242-4242",
    terminal = "69000007",
    paymentDescription = "CREDITO A VISTA",
)

internal fun failedResult(error: PaymentError, reason: String = "motivo") =
    PaymentResult.Failed(error, reason)
