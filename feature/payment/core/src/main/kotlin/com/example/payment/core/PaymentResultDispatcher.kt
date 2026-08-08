package com.example.payment.core

/**
 * Ponte entre quem recebe o retorno do app de pagamento e o [PaymentResultSource].
 *
 * A divisão de responsabilidade é proposital:
 * - **quem chama** decide se aquilo que o sistema entregou é candidato a retorno de pagamento e
 *   extrai o deep link — no Android, filtrar a Intent e pegar o `data`, já que a mesma tela recebe
 *   outras Intents (abertura pelo launcher, outros deep links);
 * - **a implementação** decide se aquela URI é de fato um retorno seu e como decodificá-la.
 *
 * Receber `String` em vez de um tipo de plataforma é o que mantém este módulo sem dependência de
 * framework.
 */
interface PaymentResultDispatcher {

    /**
     * @param deepLink a URI recebida, em texto.
     * @return `true` se era um retorno de pagamento e foi publicado em [PaymentResultSource].
     */
    fun dispatch(deepLink: String): Boolean
}
