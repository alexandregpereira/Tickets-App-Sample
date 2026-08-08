package com.example.payment.core

/**
 * Cobra um pedido e **suspende até o desfecho**.
 *
 * Uma chamada, um resultado: quem pede o pagamento não precisa saber que no meio do caminho existe
 * um app externo assumindo a tela, nem observar canal nenhum para descobrir como terminou.
 *
 * Nunca lança e nunca deixa a chamada sem resposta — desistência do usuário e ausência de app de
 * pagamento também voltam como [PaymentResult.Failed].
 */
fun interface StartPaymentUseCase {

    suspend operator fun invoke(order: PaymentOrder): PaymentResult
}
