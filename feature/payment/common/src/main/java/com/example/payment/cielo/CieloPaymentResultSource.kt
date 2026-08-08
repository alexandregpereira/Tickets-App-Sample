package com.example.payment.cielo

import android.content.Intent
import com.example.payment.core.PaymentResult
import com.example.payment.core.PaymentResultDispatcher
import com.example.payment.core.PaymentResultSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementa o retorno de pagamento sobre o deep link `order://response` da Cielo Smart.
 *
 * Acumula os dois papéis de propósito: recebe a Intent bruta da Activity ([dispatch]) e publica o
 * desfecho já traduzido ([results]). Assim o extrair-query-param + Base64 + JSON fica todo aqui, e
 * nem a Activity nem o checkout precisam conhecer o formato da Cielo.
 */
internal class CieloPaymentResultSource(
    private val resultBus: CieloResultBus,
    private val responseParser: CieloResponseParser,
) : PaymentResultSource, PaymentResultDispatcher {

    override val results: Flow<PaymentResult> = resultBus.responses.map(responseParser::parse)

    override fun hasPendingResult(): Boolean = resultBus.hasPendingResponse()

    override fun consume() = resultBus.consume()

    override fun dispatch(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_VIEW) return false
        val encodedResponse = intent.data?.getQueryParameter(QUERY_PARAM_RESPONSE) ?: return false
        resultBus.post(encodedResponse)
        return true
    }

    private companion object {
        const val QUERY_PARAM_RESPONSE = "response"
    }
}
