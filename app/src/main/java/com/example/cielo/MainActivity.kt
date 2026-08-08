package com.example.cielo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cielo.ui.TicketsNavHost
import com.example.ui.theme.TicketsTheme

/**
 * Única Activity do app: hospeda todo o Compose.
 *
 * Não sabe nada sobre pagamento. O retorno da adquirente chega a uma Activity própria dentro de
 * `feature:payment:common`, que devolve o resultado pela Activity Result API.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TicketsTheme {
                TicketsNavHost()
            }
        }
    }
}
