package com.example.payment.core

import kotlinx.coroutines.flow.Flow

/**
 * Canal por onde chega o desfecho de um pagamento iniciado por [StartPaymentUseCase].
 *
 * A implementação retém o último resultado até que alguém o consuma: durante o pagamento a tela que
 * espera o desfecho costuma estar em background (um app externo assumiu o primeiro plano), e um
 * resultado emitido sem coletor se perderia.
 */
interface PaymentResultSource {

    val results: Flow<PaymentResult>

    /**
     * Há um desfecho publicado e ainda não processado.
     *
     * Permite distinguir "o pagamento respondeu e o resultado está a caminho" de "o usuário saiu do
     * app de pagamento sem concluir", cenário em que nenhum retorno é gerado.
     */
    fun hasPendingResult(): Boolean

    fun consume()
}
