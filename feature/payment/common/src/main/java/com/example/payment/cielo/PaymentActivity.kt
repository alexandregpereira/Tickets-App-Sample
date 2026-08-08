package com.example.payment.cielo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Tela-ponte do pagamento: abre o app da Cielo Smart, recebe o `order://response` e devolve o
 * desfecho pela Activity Result API.
 *
 * Não desenha nada (tema translúcido) — existe para ter um ciclo de vida e um `intent-filter`
 * próprios. É isso que permite ao resto do app tratar pagamento como uma chamada que suspende e
 * devolve um resultado, sem barramento nem `onNewIntent` espalhado pela Activity principal.
 */
internal class PaymentActivity : ComponentActivity() {

    private val uiModel: PaymentUiModel by viewModel {
        parametersOf(intent.getStringExtra(EXTRA_PAYMENT_DEEP_LINK).orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mesmo tratamento de barras de sistema da tela que fica atrás, para que trazer esta Activity
        // para a frente não mexa no layout nem faça as barras entrarem e saírem.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_PAYMENT_DEEP_LINK).isNullOrBlank()) {
            // Instância criada sem pedido — por exemplo, um `order://response` que chegou depois de
            // esta tela já ter se encerrado. Não há ninguém esperando resultado: só sai de cena.
            finish()
            return
        }
        observeActions()
    }

    override fun onStart() {
        super.onStart()
        uiModel.onScreenStart()
    }

    override fun onResume() {
        super.onResume()
        uiModel.onScreenResume()
    }

    override fun onPause() {
        super.onPause()
        uiModel.onScreenPause()
    }

    /** O retorno da Cielo chega aqui: esta Activity é dona do contrato `order://response`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString() ?: return
        uiModel.onDeepLinkResult(deepLink)
    }

    private fun observeActions() {
        // Coleta enquanto a Activity existir, inclusive pausada: o desfecho pode chegar antes de
        // voltarmos ao primeiro plano.
        lifecycleScope.launch {
            uiModel.actions.collect { action ->
                when (action) {
                    is PaymentUiAction.OpenDeepLink -> openCieloApp(action.uri)
                    is PaymentUiAction.FinishWithResponse -> finishWithResponse(action.encodedResponse)
                    is PaymentUiAction.FinishWithError -> finishWithError(action.outcome)
                }
                // Confirma o consumo: uma Activity recriada não deve receber de novo o que esta já
                // tratou — em especial o pedido de abrir o app de pagamento.
                uiModel.onActionHandled()
            }
        }
    }

    /**
     * Sem `FLAG_ACTIVITY_NEW_TASK` de propósito: a Cielo abre na mesma task, então o botão voltar
     * dela retorna para cá — é o que torna a detecção de desistência confiável.
     */
    private fun openCieloApp(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (e: ActivityNotFoundException) {
            uiModel.onLaunchFailed()
        }
    }

    private fun finishWithResponse(encodedResponse: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ENCODED_RESPONSE, encodedResponse))
        finish()
    }

    private fun finishWithError(outcome: PaymentOutcome) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_OUTCOME, outcome.name))
        finish()
    }

    companion object {
        /** Deep link `lio://payment?...` que esta tela deve abrir. */
        const val EXTRA_PAYMENT_DEEP_LINK = "payment_deep_link"

        /** Conteúdo Base64 do parâmetro `response` devolvido pela Cielo. */
        const val EXTRA_ENCODED_RESPONSE = "encoded_response"

        /** Nome de um [PaymentOutcome], quando o desfecho foi decidido aqui e não pela adquirente. */
        const val EXTRA_OUTCOME = "outcome"
    }
}
