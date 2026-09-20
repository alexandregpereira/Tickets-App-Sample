package com.example.shop.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shop.core.Event
import com.example.ui.CollectUiActions
import com.example.core.money.formatAsBrl
import org.koin.androidx.compose.koinViewModel

/**
 * The module's public entry point: app navigation only needs to know where to go next.
 *
 * The UiModel is deliberately absent from the signature — it is `internal`, and that is what keeps
 * the list's state a detail of this module.
 */
@Composable
fun EventListScreen(
    onNavigateToCheckout: (eventId: String) -> Unit,
) {
    val uiModel: EventListUiModel = koinViewModel()
    val state by uiModel.state.collectAsStateWithLifecycle()

    CollectUiActions(uiModel.actions, onHandled = uiModel::onActionHandled) { action ->
        when (action) {
            is EventListUiAction.NavigateToCheckout -> onNavigateToCheckout(action.eventId)
        }
    }

    EventListContent(
        state = state,
        onEventClick = uiModel::onEventClick,
        onRetryClick = uiModel::onRetryClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventListContent(
    state: EventListUiState,
    onEventClick: (String) -> Unit,
    onRetryClick: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Eventos") }) },
    ) { innerPadding ->
        when {
            state.isLoading -> CenteredBox(Modifier.padding(innerPadding)) {
                CircularProgressIndicator()
            }

            state.errorMessage != null -> CenteredBox(Modifier.padding(innerPadding)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.errorMessage, textAlign = TextAlign.Center)
                    Button(onClick = onRetryClick) { Text("Tentar novamente") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.events, key = Event::id) { event ->
                    EventCard(event = event, onClick = { onEventClick(event.id) })
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: Event, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.priceInCents.formatAsBrl(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
