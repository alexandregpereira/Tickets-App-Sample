package com.example.checkout

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.PaymentResultSource
import com.example.payment.core.StartPaymentResult
import com.example.payment.core.StartPaymentUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Dublês do contrato `payment:core`.
 *
 * O checkout é testado sem nenhum vestígio de adquirente: o que importa aqui é *qual pedido* foi
 * enviado e *como* a tela reage ao desfecho — o formato do deep link é assunto de
 * `feature:payment:common`.
 */
internal class FakeStartPaymentUseCase : StartPaymentUseCase {

    /** Todo pedido recebido, inclusive os que falharam ao abrir. */
    val orders = mutableListOf<PaymentOrder>()

    /** Pedidos cujo fluxo de pagamento realmente abriu. */
    val launchedOrders = mutableListOf<PaymentOrder>()

    var failNextPayment = false

    override suspend fun invoke(order: PaymentOrder): StartPaymentResult {
        orders += order
        if (failNextPayment) {
            failNextPayment = false
            return StartPaymentResult.Failed(
                error = PaymentError.APP_NOT_FOUND,
                reason = "App de pagamento não encontrado.",
            )
        }
        launchedOrders += order
        return StartPaymentResult.Launched
    }
}

internal class FakePaymentResultSource : PaymentResultSource {

    private val _results = MutableSharedFlow<PaymentResult>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val results: Flow<PaymentResult> = _results

    override fun hasPendingResult(): Boolean = _results.replayCache.isNotEmpty()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun consume() = _results.resetReplayCache()

    fun post(result: PaymentResult) {
        _results.tryEmit(result)
    }
}

internal fun approvedResult(reference: String) = PaymentResult.Approved(
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
