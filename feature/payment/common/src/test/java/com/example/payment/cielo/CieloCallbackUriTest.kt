package com.example.payment.cielo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CieloCallbackUriTest {

    @Test
    fun `extracts the response parameter`() {
        val response = CieloCallbackUri.encodedResponseOrNull(
            "order://response?response=eyJjb2RlIjoxfQ==&responsecode=0"
        )

        // O valor tem '=' de padding: só o primeiro '=' separa chave de valor.
        assertEquals("eyJjb2RlIjoxfQ==", response)
    }

    @Test
    fun `keeps plus signs, which are valid base64`() {
        val response = CieloCallbackUri.encodedResponseOrNull("order://response?response=ab+cd/ef=")

        assertEquals("ab+cd/ef=", response)
    }

    @Test
    fun `decodes percent escapes`() {
        val response = CieloCallbackUri.encodedResponseOrNull("order://response?response=a%2Bb%3D")

        assertEquals("a+b=", response)
    }

    @Test
    fun `ignores deep links that are not the payment callback`() {
        assertNull(CieloCallbackUri.encodedResponseOrNull("outro://response?response=x"))
        assertNull(CieloCallbackUri.encodedResponseOrNull("order://outra?response=x"))
    }

    @Test
    fun `returns null when the response parameter is missing or empty`() {
        assertNull(CieloCallbackUri.encodedResponseOrNull("order://response"))
        assertNull(CieloCallbackUri.encodedResponseOrNull("order://response?responsecode=0"))
        assertNull(CieloCallbackUri.encodedResponseOrNull("order://response?response="))
    }
}
