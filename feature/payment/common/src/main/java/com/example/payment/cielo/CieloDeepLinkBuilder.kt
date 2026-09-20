package com.example.payment.cielo

import kotlinx.serialization.json.Json

/**
 * Builds the Cielo Smart checkout URI.
 *
 * The local integration uses no SDK: the app serializes the order to JSON, encodes it as Base64 and
 * opens `lio://payment?request=<base64>&urlCallback=order://response` with an ACTION_VIEW Intent.
 * See https://docs.cielo.com.br/cielo-smart/docs/pagamento
 *
 * A pure class (no Android framework dependency) so it stays testable on the JVM.
 */
internal class CieloDeepLinkBuilder(
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
         * Parts of the response contract declared in the AndroidManifest: they must be identical
         * to the manifest's `<data android:scheme="order" android:host="response" />`.
         * [CieloPaymentResultSource] uses both to recognize the response.
         */
        const val CALLBACK_SCHEME = "order"
        const val CALLBACK_HOST = "response"

        /** The same contract as a URI, sent in the payment request's `urlCallback`. */
        const val CALLBACK_URI = "$CALLBACK_SCHEME://$CALLBACK_HOST"

        /** Package of Cielo's integration service, declared in `<queries>` (Android 11+). */
        const val CIELO_URI_APP_PACKAGE = "com.ads.lio.uriappclient"
    }
}
