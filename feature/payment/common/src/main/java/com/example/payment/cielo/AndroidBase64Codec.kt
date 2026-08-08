package com.example.payment.cielo

import android.util.Base64

/**
 * Implementação de [Base64Codec] sobre `android.util.Base64`, disponível desde a API 8.
 *
 * A codificação usa NO_WRAP porque o resultado vai dentro de uma query string de URI — quebras de
 * linha invalidariam o deep link.
 */
internal class AndroidBase64Codec : Base64Codec {

    override fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    override fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.DEFAULT)
}
