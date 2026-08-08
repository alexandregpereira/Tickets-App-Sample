package com.example.cielo.cielo

/**
 * Resultado de uma transação devolvida pela Cielo Smart em `order://response`.
 */
sealed interface CieloPaymentResult {

    /** Pagamento autorizado (`paymentFields.statusCode` 0 para Pix ou 1 para autorizado). */
    data class Approved(
        val orderId: String,
        val reference: String?,
        val paidAmountInCents: Long,
        val authCode: String?,
        val cieloCode: String?,
        val brand: String?,
        val maskedCard: String?,
        val terminal: String?,
        /**
         * Forma de pagamento efetivamente escolhida pelo portador na tela da Cielo Smart —
         * ex.: "CREDITO A VISTA", "DEBITO VISTA", "PIX PAGAMENTO". O app não a define na requisição,
         * então este é o único lugar de onde ela pode vir.
         */
        val paymentDescription: String?,
    ) : CieloPaymentResult

    /**
     * Transação não concluída. Cobre tanto o payload de erro da Cielo
     * (`{"code": 1, "reason": "CANCELADO PELO USUÁRIO"}`) quanto falhas locais de integração.
     */
    data class Failed(
        val error: CieloPaymentError,
        val reason: String,
    ) : CieloPaymentResult
}

/**
 * Códigos de erro do deep link, conforme
 * https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro
 *
 * [APP_NOT_FOUND] e [INVALID_RESPONSE] não vêm da Cielo: representam falhas locais de integração
 * (app/emulador ausente e resposta ilegível, respectivamente).
 */
enum class CieloPaymentError(val code: Int?) {
    CANCELLED_BY_USER(1),
    GENERIC(2),
    PAYMENT(3),
    AUTHENTICATION(4),
    APP_NOT_FOUND(null),
    INVALID_RESPONSE(null);

    companion object {
        fun fromCode(code: Int): CieloPaymentError = when (code) {
            1 -> CANCELLED_BY_USER
            2 -> GENERIC
            3 -> PAYMENT
            4 -> AUTHENTICATION
            else -> GENERIC
        }
    }
}
