package com.example.shop.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shop.GetEventsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The event list UiModel.
 *
 * The UI observes [state] and sends intents by calling the public functions; one-shot effects go out
 * through [actions].
 */
internal class EventListUiModel(
    private val getEvents: GetEventsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EventListUiState())
    val state: StateFlow<EventListUiState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<EventListUiAction>(replay = 1)
    val actions: SharedFlow<EventListUiAction> = _actions.asSharedFlow()

    init {
        loadEvents()
    }

    fun onRetryClick() = loadEvents()

    fun onEventClick(eventId: String) {
        _actions.tryEmit(EventListUiAction.NavigateToCheckout(eventId))
    }

    /** The UI signals it has handled the last action, releasing the replay. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onActionHandled() = _actions.resetReplayCache()

    private fun loadEvents() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { getEvents() }
                .onSuccess { events ->
                    _state.update { it.copy(isLoading = false, events = events) }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Não foi possível carregar os eventos.",
                        )
                    }
                }
        }
    }
}
