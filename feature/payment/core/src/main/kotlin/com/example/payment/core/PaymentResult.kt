package com.example.payment.core

/**
 * Desfecho de uma cobrança, já traduzido do vocabulário da adquirente.
 */
sealed interface PaymentResult {

    data class Approved(
        /** Identificador do pedido na adquirente. */
        val orderId: String,
        /** Referência de idempotência ecoada de volta, quando a adquirente a devolve. */
        val reference: String?,
        val paidAmountInCents: Long,
        val authorizationCode: String?,
        val acquirerCode: String?,
        val brand: String?,
        val maskedCard: String?,
        val terminal: String?,
        /**
         * Forma de pagamento efetivamente escolhida pelo portador — ex.: "CREDITO A VISTA",
         * "PIX PAGAMENTO". Quem inicia o pagamento não a define, então este é o único lugar de
         * onde ela pode vir.
         */
        val paymentDescription: String?,
    ) : PaymentResult

    data class Failed(val error: PaymentError, val reason: String) : PaymentResult
}

enum class PaymentError {
    CANCELLED_BY_USER,
    GENERIC,
    PAYMENT,
    AUTHENTICATION,

    /** Não há app de pagamento instalado para atender à cobrança. */
    APP_NOT_FOUND,

    /** A resposta chegou, mas não foi possível interpretá-la. */
    INVALID_RESPONSE,

    /**
     * O usuário saiu do fluxo antes de qualquer desfecho — voltou sem pagar nem cancelar.
     *
     * Diferente de [CANCELLED_BY_USER], que é uma recusa vinda da adquirente: aqui a cobrança nunca
     * chegou a ser tentada, então não há o que registrar nem comprovante a exibir.
     */
    ABANDONED,
}
