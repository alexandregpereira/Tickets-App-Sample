package com.example.cielo.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.checkout.CheckoutScreen
import com.example.shop.list.EventListScreen
import com.example.checkout.purchase.receipt.ReceiptScreen
import kotlinx.serialization.Serializable

@Serializable
object EventListRoute

@Serializable
data class CheckoutRoute(val eventId: String)

@Serializable
data class ReceiptRoute(val purchaseReference: String)

/**
 * The app's navigation graph — a single Activity, three Compose screens.
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
                onNavigateToReceipt = { reference, isApproved ->
                    navController.navigate(ReceiptRoute(reference)) {
                        // Purchase completed: there is nothing to go back to in its checkout. On a
                        // decline or cancellation the checkout stays, so the user can try again.
                        if (isApproved) popUpTo<CheckoutRoute> { inclusive = true }
                    }
                },
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
