package com.example.cielo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.cielo.checkout.CheckoutScreen
import com.example.cielo.event.list.EventListScreen
import com.example.cielo.purchase.receipt.ReceiptScreen
import kotlinx.serialization.Serializable

@Serializable
object EventListRoute

@Serializable
data class CheckoutRoute(val eventId: String)

@Serializable
data class ReceiptRoute(val purchaseReference: String)

/**
 * Grafo de navegação do app — uma única Activity, três telas em Compose.
 */
@Composable
fun TicketsNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = EventListRoute) {
        composable<EventListRoute> {
            EventListScreen(
                onNavigateToCheckout = { eventId -> navController.navigate(CheckoutRoute(eventId)) },
            )
        }

        composable<CheckoutRoute> { backStackEntry ->
            CheckoutScreen(
                eventId = backStackEntry.toRoute<CheckoutRoute>().eventId,
                onNavigateToReceipt = { reference -> navController.navigate(ReceiptRoute(reference)) },
                onNavigateUp = { navController.popBackStack() },
            )
        }

        composable<ReceiptRoute> { backStackEntry ->
            ReceiptScreen(
                purchaseReference = backStackEntry.toRoute<ReceiptRoute>().purchaseReference,
                onDoneClick = {
                    navController.popBackStack(route = EventListRoute, inclusive = false)
                },
            )
        }
    }
}
