package com.example.checkout

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase

/**
 * Dublê do contrato `payment:core`.
 *
 * O checkout é testado sem nenhum vestígio de adquirente: o que importa é *qual pedido* foi enviado
 * e *como* a tela reage ao desfecho — o formato do deep link é assunto de `feature:payment:common`.
 */
internal class FakeStartPaymentUseCase : StartPaymentUseCase {

    /** Todo pedido recebido, na ordem. */
    val orders = mutableListOf<PaymentOrder>()

    /** Desfecho devolvido na próxima chamada. */
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
