package com.example.payment.core

/**
 * Inicia a cobrança de um pedido.
 *
 * O contrato é deliberadamente assíncrono em duas etapas: esta chamada apenas **abre** o fluxo de
 * pagamento e devolve se conseguiu abri-lo. O desfecho (aprovado, negado, cancelado) chega depois
 * por [PaymentResultSource], porque no meio do caminho existe um app externo que assume a tela.
 */
fun interface StartPaymentUseCase {

    suspend operator fun invoke(order: PaymentOrder): StartPaymentResult
}

sealed interface StartPaymentResult {

    /** Fluxo de pagamento aberto; o desfecho virá por [PaymentResultSource]. */
    data object Launched : StartPaymentResult

    /** Não foi possível nem abrir o fluxo — logo, não houve cobrança e dá para tentar de novo. */
    data class Failed(val error: PaymentError, val reason: String) : StartPaymentResult
}
