package com.example.payment.cielo

import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult
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
 * Nunca lança: qualquer payload inesperado vira [PaymentError.INVALID_RESPONSE], porque uma
 * exceção aqui deixaria o usuário sem saber se foi cobrado.
 */
internal class CieloResponseParser(
    private val base64Codec: Base64Codec,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun parse(encodedResponse: String?): PaymentResult {
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
    private fun JsonObject.parseError(): PaymentResult.Failed? {
        val code = this["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val error = code.toPaymentError()
        val reason = this["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: defaultReasonFor(error)
        return PaymentResult.Failed(error, reason)
    }

    private fun JsonObject.parseApprovedOrder(): PaymentResult {
        val payment = this["payments"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return PaymentResult.Failed(
                error = PaymentError.PAYMENT,
                reason = "A Cielo não retornou nenhuma transação para este pedido.",
            )

        val paymentFields = payment["paymentFields"]?.jsonObject
        // statusCode: 0 = Pix, 1 = autorizada, 2 = cancelada.
        val statusCode = paymentFields?.get("statusCode")?.jsonPrimitive?.content
        if (statusCode == CANCELLED_STATUS_CODE) {
            return PaymentResult.Failed(
                error = PaymentError.CANCELLED_BY_USER,
                reason = "Transação cancelada.",
            )
        }

        return PaymentResult.Approved(
            orderId = this["id"]?.jsonPrimitive?.content.orEmpty(),
            reference = this["reference"]?.jsonPrimitive?.content,
            paidAmountInCents = this["paidAmount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            authorizationCode = payment["authCode"]?.jsonPrimitive?.content,
            acquirerCode = payment["cieloCode"]?.jsonPrimitive?.content,
            brand = payment["brand"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            maskedCard = payment["mask"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            terminal = payment["terminal"]?.jsonPrimitive?.content,
            paymentDescription = paymentFields.paymentDescription(),
        )
    }

    /**
     * Monta a descrição da forma de pagamento escolhida no terminal.
     *
     * Prioriza `primaryProductName` + `secondaryProductName` ("CREDITO" + "A VISTA") em vez de
     * `productName`: embora a documentação descreva `productName` como a "forma de pagamento
     * compilada", o Emulador Cielo devolve nele um texto fixo de mock, enquanto os dois campos de
     * produto refletem de fato a escolha do portador.
     *
     * Quando o nome secundário já começa pelo primário — "CREDITO" + "CREDITO VISTA", como no
     * emulador — usa só o secundário, para não repetir a palavra.
     */
    private fun JsonObject?.paymentDescription(): String? {
        val primary = this?.text("primaryProductName")
        val secondary = this?.text("secondaryProductName")
        return when {
            primary == null -> secondary ?: this?.text("productName")
            secondary == null -> primary
            secondary.startsWith(primary, ignoreCase = true) -> secondary
            else -> "$primary $secondary"
        }
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    /** Códigos documentados em https://docs.cielo.com.br/cielo-smart/docs/codigos-de-erro */
    private fun Int.toPaymentError(): PaymentError = when (this) {
        1 -> PaymentError.CANCELLED_BY_USER
        2 -> PaymentError.GENERIC
        3 -> PaymentError.PAYMENT
        4 -> PaymentError.AUTHENTICATION
        else -> PaymentError.GENERIC
    }

    private fun invalid(reason: String) =
        PaymentResult.Failed(PaymentError.INVALID_RESPONSE, reason)

    private fun defaultReasonFor(error: PaymentError) = when (error) {
        PaymentError.CANCELLED_BY_USER -> "Pagamento cancelado pelo usuário."
        PaymentError.PAYMENT -> "Erro no pagamento."
        PaymentError.AUTHENTICATION -> "Erro de autenticação com a Cielo."
        else -> "Erro genérico na integração com a Cielo."
    }

    private companion object {
        const val CANCELLED_STATUS_CODE = "2"
    }
}
