package com.example.cielo.cielo

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Abre o app de integração da Cielo Smart a partir de uma URI de deep link.
 *
 * Existe como interface para que o `StartPaymentUseCase` continue testável na JVM e para isolar a
 * única parte do fluxo de pagamento que toca o framework Android.
 */
fun interface CieloCheckoutLauncher {
    /** @throws android.content.ActivityNotFoundException se a Cielo Smart não estiver instalada. */
    fun launch(uri: String)
}

/**
 * Usa o contexto de aplicação com `FLAG_ACTIVITY_NEW_TASK`, como recomendado em
 * https://docs.cielo.com.br/cielo-smart/docs/servico-em-primeiro-plano
 */
class AndroidCieloCheckoutLauncher(
    private val context: Context,
) : CieloCheckoutLauncher {

    override fun launch(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
