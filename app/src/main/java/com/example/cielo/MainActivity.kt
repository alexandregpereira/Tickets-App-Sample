package com.example.cielo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cielo.cielo.CieloResultBus
import com.example.cielo.ui.TicketsNavHost
import com.example.cielo.ui.theme.CieloSmartTheme
import org.koin.android.ext.android.inject

/**
 * Única Activity do app: hospeda todo o Compose e recebe o retorno da Cielo Smart.
 *
 * A Cielo responde ao pagamento abrindo `order://response?response=<base64>&responsecode=0` como uma
 * nova Intent ACTION_VIEW (ver https://docs.cielo.com.br/cielo-smart/docs/recuperando-dados). Com
 * `launchMode="singleTop"` no manifest, essa Intent chega em [onNewIntent] sem recriar a Activity —
 * os UiModels sobrevivem e conseguem reconciliar o pagamento em andamento.
 */
class MainActivity : ComponentActivity() {

    private val cieloResultBus: CieloResultBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Cobre o caso em que o processo foi morto e a Intent de resposta recria a Activity.
        publishCieloResponse(intent)
        setContent {
            CieloSmartTheme {
                TicketsNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishCieloResponse(intent)
    }

    private fun publishCieloResponse(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val encodedResponse = intent.data?.getQueryParameter(QUERY_PARAM_RESPONSE) ?: return
        cieloResultBus.post(encodedResponse)
    }

    private companion object {
        const val QUERY_PARAM_RESPONSE = "response"
    }
}
