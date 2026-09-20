package com.example.payment.cielo

/**
 * An abstraction over Base64.
 *
 * `android.util.Base64` is a stub class in JVM unit tests (it returns 0 / throws
 * `RuntimeException`), so [CieloDeepLinkBuilder] and [CieloResponseParser] depend on this interface
 * instead. In production we use [AndroidBase64Codec]; in tests, an implementation backed by
 * `java.util.Base64`.
 */
internal interface Base64Codec {
    fun encode(bytes: ByteArray): String
    fun decode(value: String): ByteArray
}
