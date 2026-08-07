package com.example.cielo.cielo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Interpreta o parâmetro `response` devolvido pela Cielo Smart em `order://response`.
 *
 * O conteúdo é um JSON em Base64 que pode ser:
 * - o pedido pago (objeto Order com `payments[]`), ou
 * - um erro no formato `{"code": 1, "reason": "CANCELADO PELO USUÁRIO"}`.
 *
 * Ver https://docs.cielo.com.br/cielo-smart/docs/recuperando-dados e
 * https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro
 *
 * Nunca lança: qualquer payload inesperado vira [CieloPaymentError.INVALID_RESPONSE], porque uma
 * exceção aqui deixaria o usuário sem saber se foi cobrado.
 */
class CieloResponseParser(
    private val base64Codec: Base64Codec,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun parse(encodedResponse: String?): CieloPaymentResult {
        if (encodedResponse.isNullOrBlank()) {
            return invalid("Resposta da Cielo veio vazia.")
        }
        val root = runCatching {
            json.parseToJsonElement(String(base64Codec.decode(encodedResponse))).jsonObject
        }.getOrElse {
            return invalid("Não foi possível ler a resposta da Cielo.")
        }

        return runCatching {
            root.parseError() ?: root.parseApprovedOrder()
        }.getOrElse {
            invalid("Resposta da Cielo em formato inesperado.")
        }
    }

    /** `{"code": Int, "reason": String}` — só o payload de erro tem `code` na raiz. */
    private fun JsonObject.parseError(): CieloPaymentResult.Failed? {
        val code = this["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val error = CieloPaymentError.fromCode(code)
        val reason = this["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: defaultReasonFor(error)
        return CieloPaymentResult.Failed(error, reason)
    }

    private fun JsonObject.parseApprovedOrder(): CieloPaymentResult {
        val payment = this["payments"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return CieloPaymentResult.Failed(
                error = CieloPaymentError.PAYMENT,
                reason = "A Cielo não retornou nenhuma transação para este pedido.",
            )

        val paymentFields = payment["paymentFields"]?.jsonObject
        // statusCode: 0 = Pix, 1 = autorizada, 2 = cancelada.
        val statusCode = paymentFields?.get("statusCode")?.jsonPrimitive?.content
        if (statusCode == CANCELLED_STATUS_CODE) {
            return CieloPaymentResult.Failed(
                error = CieloPaymentError.CANCELLED_BY_USER,
                reason = "Transação cancelada.",
            )
        }

        return CieloPaymentResult.Approved(
            orderId = this["id"]?.jsonPrimitive?.content.orEmpty(),
            reference = this["reference"]?.jsonPrimitive?.content,
            paidAmountInCents = this["paidAmount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            authCode = payment["authCode"]?.jsonPrimitive?.content,
            cieloCode = payment["cieloCode"]?.jsonPrimitive?.content,
            brand = payment["brand"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            maskedCard = payment["mask"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            terminal = payment["terminal"]?.jsonPrimitive?.content,
            productName = paymentFields?.get("productName")?.jsonPrimitive?.content,
        )
    }

    private fun invalid(reason: String) =
        CieloPaymentResult.Failed(CieloPaymentError.INVALID_RESPONSE, reason)

    private fun defaultReasonFor(error: CieloPaymentError) = when (error) {
        CieloPaymentError.CANCELLED_BY_USER -> "Pagamento cancelado pelo usuário."
        CieloPaymentError.PAYMENT -> "Erro no pagamento."
        CieloPaymentError.AUTHENTICATION -> "Erro de autenticação com a Cielo."
        else -> "Erro genérico na integração com a Cielo."
    }

    private companion object {
        const val CANCELLED_STATUS_CODE = "2"
    }
}
