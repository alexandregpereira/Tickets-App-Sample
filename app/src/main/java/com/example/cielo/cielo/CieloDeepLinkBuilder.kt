package com.example.cielo.cielo

import kotlinx.serialization.json.Json

/**
 * Monta a URI de checkout da Cielo Smart.
 *
 * A integração local não usa SDK: o app serializa o pedido em JSON, converte para Base64 e abre
 * `lio://payment?request=<base64>&urlCallback=order://response` com uma Intent ACTION_VIEW.
 * Ver https://docs.cielo.com.br/cielo-smart/docs/pagamento
 *
 * Classe pura (sem dependência de framework Android) para ser testável na JVM.
 */
class CieloDeepLinkBuilder(
    private val base64Codec: Base64Codec,
    private val json: Json = Json { encodeDefaults = true },
) {

    fun buildPaymentUri(request: CieloPaymentRequest): String {
        val payload = base64Codec.encode(json.encodeToString(request).toByteArray())
        return "$PAYMENT_URI?request=$payload&urlCallback=$CALLBACK_URI"
    }

    companion object {
        const val PAYMENT_URI = "lio://payment"

        /**
         * Contrato de resposta declarado no AndroidManifest da MainActivity. Precisa ser idêntico
         * ao `<data android:scheme="order" android:host="response" />` do manifest.
         */
        const val CALLBACK_URI = "order://response"

        /** Pacote do serviço de integração da Cielo, declarado em `<queries>` (Android 11+). */
        const val CIELO_URI_APP_PACKAGE = "com.ads.lio.uriappclient"
    }
}
