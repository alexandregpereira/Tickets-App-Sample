package com.example.payment.cielo

import androidx.core.net.toUri
import com.example.payment.core.PaymentResult
import com.example.payment.core.PaymentResultDispatcher
import com.example.payment.core.PaymentResultSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementa o retorno de pagamento sobre o deep link `order://response` da Cielo Smart.
 *
 * Acumula os dois papéis de propósito: recebe o deep link cru de quem o interceptou ([dispatch]) e
 * publica o desfecho já traduzido ([results]). Assim o reconhecer-a-URI + extrair-query-param +
 * Base64 + JSON fica todo aqui, e nem a Activity nem o checkout precisam conhecer o formato da
 * Cielo.
 */
internal class CieloPaymentResultSource(
    private val resultBus: CieloResultBus,
    private val responseParser: CieloResponseParser,
) : PaymentResultSource, PaymentResultDispatcher {

    override val results: Flow<PaymentResult> = resultBus.responses.map(responseParser::parse)

    override fun hasPendingResult(): Boolean = resultBus.hasPendingResponse()

    override fun consume() = resultBus.consume()

    override fun dispatch(deepLink: String): Boolean {
        // Quem chama já filtrou o que é deep link; aqui decidimos se ele é *nosso*, comparando com o
        // mesmo `urlCallback` que enviamos na requisição de pagamento.
        val uri = runCatching { deepLink.toUri() }.getOrElse { return false }
        if (uri.scheme != CieloDeepLinkBuilder.CALLBACK_SCHEME) return false
        if (uri.host != CieloDeepLinkBuilder.CALLBACK_HOST) return false

        val encodedResponse = runCatching { uri.getQueryParameter(QUERY_PARAM_RESPONSE) }
            .getOrNull() ?: return false
        resultBus.post(encodedResponse)
        return true
    }

    private companion object {
        const val QUERY_PARAM_RESPONSE = "response"
    }
}
