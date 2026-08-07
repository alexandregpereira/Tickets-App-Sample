package com.example.cielo.cielo

/**
 * Subconjunto dos valores aceitos no campo `paymentCode` da requisição de pagamento.
 *
 * A lista completa está em
 * https://docs.cielo.com.br/cielo-smart/docs/valores-aceitos-no-campo-paymentcode
 * Aqui expomos apenas os três meios relevantes para a venda de ingressos.
 */
enum class CieloPaymentCode(val value: String, val label: String) {
    CREDITO_AVISTA("CREDITO_AVISTA", "Crédito à vista"),
    DEBITO_AVISTA("DEBITO_AVISTA", "Débito à vista"),
    PIX("PIX", "Pix"),
}
