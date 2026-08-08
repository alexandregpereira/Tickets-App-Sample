package com.example.cielo.cielo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload de pagamento enviado à Cielo Smart via deep link.
 *
 * Formato definido em https://docs.cielo.com.br/cielo-smart/docs/pagamento
 * Todos os valores monetários são inteiros em centavos.
 */
@Serializable
data class CieloPaymentRequest(
    val accessToken: String,
    @SerialName("clientID") val clientId: String,
    /** Chave de idempotência do pedido: reenvios da mesma compra repetem esta referência. */
    val reference: String,
    val installments: Int = 0,
    val items: List<CieloPaymentItem>,
    /** Valor total em centavos, enviado como string conforme a documentação. */
    val value: String,
)

@Serializable
data class CieloPaymentItem(
    val name: String,
    val quantity: Int,
    val sku: String,
    val unitOfMeasure: String = "unidade",
    /** Preço unitário em centavos. */
    val unitPrice: Long,
)
