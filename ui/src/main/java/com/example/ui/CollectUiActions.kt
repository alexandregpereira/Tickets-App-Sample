package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a UiModel's action SharedFlow while the screen is at least STARTED and acknowledges
 * consumption through [onHandled].
 *
 * The ack is needed because UiModels publish actions with `replay = 1`: during a payment the payment
 * app is in the foreground and this screen goes to STOPPED, so an action emitted in that window
 * would be lost by a SharedFlow without replay. With replay it is delivered as soon as the screen
 * comes back; without the ack it would be redelivered on every new collection.
 */
@Composable
fun <T> CollectUiActions(
    actions: Flow<T>,
    onHandled: () -> Unit,
    onAction: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(actions, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            actions.collect { action ->
                currentOnAction(action)
                currentOnHandled()
            }
        }
    }
}
