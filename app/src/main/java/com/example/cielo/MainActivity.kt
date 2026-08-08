package com.example.cielo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.payment.core.PaymentResultDispatcher
import com.example.cielo.ui.TicketsNavHost
import com.example.ui.theme.TicketsTheme
import org.koin.android.ext.android.inject

/**
 * Única Activity do app: hospeda todo o Compose e recebe o retorno do app de pagamento.
 *
 * O meio de pagamento responde abrindo uma nova Intent `ACTION_VIEW` neste app. Com
 * `launchMode="singleTop"` no manifest, ela chega em [onNewIntent] sem recriar a Activity — os
 * UiModels sobrevivem e conseguem reconciliar o pagamento em andamento.
 *
 * A Activity não sabe como o resultado vem codificado: só repassa a Intent ao
 * [PaymentResultDispatcher], cuja implementação vive em `feature:payment:common`.
 */
class MainActivity : ComponentActivity() {

    private val paymentResultDispatcher: PaymentResultDispatcher by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cobre o caso em que o processo foi morto e a Intent de resposta recria a Activity.
        dispatchPaymentResult(intent)
        setContent {
            TicketsTheme {
                TicketsNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchPaymentResult(intent)
    }

    /**
     * Esta Activity pode receber mais de um tipo de Intent. Filtrar qual é qual e extrair o deep link é responsabilidade daqui;
     * reconhecer e decodificar o conteúdo é do módulo de pagamento.
     */
    private fun dispatchPaymentResult(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val deepLink = intent.data?.toString() ?: return
        paymentResultDispatcher.dispatch(deepLink)
    }
}
