package com.example.payment.cielo

/**
 * Abstração sobre Base64.
 *
 * `android.util.Base64` é uma classe stub em testes unitários de JVM (retorna 0 / lança
 * `RuntimeException`), então [CieloDeepLinkBuilder] e [CieloResponseParser] dependem desta
 * interface. Em produção usamos [AndroidBase64Codec]; nos testes, uma implementação baseada em
 * `java.util.Base64`.
 */
internal interface Base64Codec {
    fun encode(bytes: ByteArray): String
    fun decode(value: String): ByteArray
}
