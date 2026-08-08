package com.example.payment.cielo

/**
 * Leitura do deep link de resposta `order://response?response=<base64>`.
 *
 * Feito em Kotlin puro, sem `android.net.Uri`, porque `Uri` é stub em teste de JVM e esta é a
 * tradução mais delicada do fluxo — vale poder cobri-la com teste rápido.
 *
 * Reproduz o comportamento do `Uri.getQueryParameter` no ponto que importa: `%XX` é decodificado,
 * mas `+` **não** vira espaço. O payload da Cielo é Base64, onde `+` é um caractere legítimo.
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
                // O valor pode conter '=' (padding do Base64), então só o primeiro separa a chave.
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
