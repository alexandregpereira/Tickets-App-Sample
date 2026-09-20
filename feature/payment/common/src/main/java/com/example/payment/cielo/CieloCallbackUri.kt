package com.example.payment.cielo

/**
 * Parses the `order://response?response=<base64>` response deep link.
 *
 * Written in pure Kotlin, without `android.net.Uri`, because `Uri` is a stub in JVM tests and this
 * is the most delicate translation in the flow — being able to cover it with fast tests is worth it.
 *
 * It reproduces `Uri.getQueryParameter`'s behavior where it matters: `%XX` is decoded, but `+` does
 * **not** become a space. Cielo's payload is Base64, where `+` is a legitimate character.
 */
internal object CieloCallbackUri {

    fun isPaymentCallback(deepLink: String): Boolean =
        deepLink.startsWith("${CieloDeepLinkBuilder.CALLBACK_SCHEME}://${CieloDeepLinkBuilder.CALLBACK_HOST}")

    fun encodedResponseOrNull(deepLink: String): String? {
        if (!isPaymentCallback(deepLink)) return null
        return deepLink.queryParameterOrNull(QUERY_PARAM_RESPONSE)?.takeIf { it.isNotBlank() }
    }

    private fun String.queryParameterOrNull(name: String): String? {
        val query = substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&')
            .firstNotNullOfOrNull { pair ->
                val separator = pair.indexOf('=')
                // The value may contain '=' (Base64 padding), so only the first one splits the key.
                if (separator < 0 || pair.substring(0, separator) != name) return@firstNotNullOfOrNull null
                pair.substring(separator + 1).percentDecoded()
            }
    }

    private fun String.percentDecoded(): String {
        if ('%' !in this) return this
        val bytes = java.io.ByteArrayOutputStream(length)
        var index = 0
        while (index < length) {
            val char = this[index]
            val hex = if (char == '%') runCatching {
                substring(index + 1, index + 3).toInt(radix = 16)
            }.getOrNull() else null

            if (hex == null) {
                bytes.write(char.code)
                index++
            } else {
                bytes.write(hex)
                index += 3
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private const val QUERY_PARAM_RESPONSE = "response"
}
