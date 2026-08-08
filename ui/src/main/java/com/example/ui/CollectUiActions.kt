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
 * Coleta o SharedFlow de ações de um UiModel enquanto a tela está ao menos STARTED e confirma o
 * consumo em [onHandled].
 *
 * O ack é necessário porque os UiModels publicam as ações com `replay = 1`: durante o pagamento a
 * app de pagamento fica em foreground e esta tela vai para STOPPED, então uma ação emitida nesse
 * intervalo seria perdida por um SharedFlow sem replay. Com replay, ela é entregue assim que a tela
 * volta; sem o ack, seria reentregue a cada nova coleta.
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
