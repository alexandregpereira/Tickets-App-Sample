package com.example.cielo.cielo

import java.util.Base64

/**
 * Implementação de [Base64Codec] para testes de JVM: `android.util.Base64` é um stub que lança
 * exceção fora do dispositivo.
 */
class JvmBase64Codec : Base64Codec {

    override fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    override fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
