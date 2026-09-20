package com.example.payment.cielo

import android.util.Base64

/**
 * A [Base64Codec] implementation on top of `android.util.Base64`, available since API 8.
 *
 * Encoding uses NO_WRAP because the result goes inside a URI query string — line breaks would
 * invalidate the deep link.
 */
internal class AndroidBase64Codec : Base64Codec {

    override fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    override fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.DEFAULT)
}
