package com.example.cielo.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cielo.ui.CollectUiActions
import com.example.cielo.ui.formatAsBrl
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CheckoutScreen(
    eventId: String,
    onNavigateToReceipt: (purchaseReference: String) -> Unit,
    onNavigateUp: () -> Unit,
    uiModel: CheckoutUiModel = koinViewModel { parametersOf(eventId) },
) {
    val state by uiModel.state.collectAsStateWithLifecycle()

    // Cobre a volta da Cielo Smart sem callback — por exemplo, o usuário sair pelo botão voltar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { uiModel.onScreenResume() }

    CollectUiActions(uiModel.actions, onHandled = uiModel::onActionHandled) { action ->
        when (action) {
            is CheckoutUiAction.NavigateToReceipt -> onNavigateToReceipt(action.purchaseReference)
        }
    }

    CheckoutContent(
        state = state,
        onNavigateUp = onNavigateUp,
        onIncreaseClick = uiModel::onIncreaseQuantityClick,
        onDecreaseClick = uiModel::onDecreaseQuantityClick,
        onPayClick = uiModel::onPayClick,
        onErrorDismiss = uiModel::onErrorDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutContent(
    state: CheckoutUiState,
    onNavigateUp: () -> Unit,
    onIncreaseClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onPayClick: () -> Unit,
    onErrorDismiss: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingressos") },
                navigationIcon = {
                    TextButton(onClick = onNavigateUp) { Text("Voltar") }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = state.event?.name ?: "Evento indisponível",
                style = MaterialTheme.typography.headlineSmall,
            )
            state.event?.let { event ->
                Text(
                    text = "${event.priceInCents.formatAsBrl()} por ingresso",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            QuantitySelector(
                quantity = state.quantity,
                canDecrease = state.canDecreaseQuantity,
                canIncrease = state.canIncreaseQuantity,
                onDecreaseClick = onDecreaseClick,
                onIncreaseClick = onIncreaseClick,
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.totalInCents.formatAsBrl(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            state.errorMessage?.let { message ->
                ErrorCard(message = message, onDismiss = onErrorDismiss)
            }

            Button(
                onClick = onPayClick,
                enabled = state.canPay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isPaymentInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Pagar com Cielo")
                }
            }

            if (state.isPaymentInFlight) {
                Text(
                    text = "Aguardando o pagamento na Cielo Smart…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Quantidade", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecreaseClick, enabled = canDecrease) { Text("−") }
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            IconButton(onClick = onIncreaseClick, enabled = canIncrease) { Text("+") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onDismiss) { Text("Entendi") }
        }
    }
}
