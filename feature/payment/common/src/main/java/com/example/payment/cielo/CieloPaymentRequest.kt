package com.example.payment.cielo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The payment payload sent to Cielo Smart over the deep link.
 *
 * Format defined at https://docs.cielo.com.br/cielo-smart/docs/pagamento
 * Every monetary value is an integer in cents.
 */
@Serializable
internal data class CieloPaymentRequest(
    val accessToken: String,
    @SerialName("clientID") val clientId: String,
    /** The order's idempotency key: resends of the same purchase repeat this reference. */
    val reference: String,
    val installments: Int = 0,
    val items: List<CieloPaymentItem>,
    /** Total amount in cents, sent as a string as the documentation requires. */
    val value: String,
)

@Serializable
internal data class CieloPaymentItem(
    val name: String,
    val quantity: Int,
    val sku: String,
    val unitOfMeasure: String = "unidade",
    /** Unit price in cents. */
    val unitPrice: Long,
)
