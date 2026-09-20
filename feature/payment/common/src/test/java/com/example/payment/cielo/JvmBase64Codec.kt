package com.example.payment.cielo

import java.util.Base64

/**
 * A [Base64Codec] implementation for JVM tests: `android.util.Base64` is a stub that throws off
 * device.
 */
class JvmBase64Codec : Base64Codec {

    override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    override fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
