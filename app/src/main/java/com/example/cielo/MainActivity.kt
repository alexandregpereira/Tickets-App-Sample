package com.example.cielo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.cielo.ui.TicketsNavHost
import com.example.ui.theme.TicketsTheme

/**
 * The app's single Activity: it hosts all of the Compose UI.
 *
 * It knows nothing about payments. The acquirer's response reaches a dedicated Activity inside
 * `feature:payment:common`, which returns the result through the Activity Result API.
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
