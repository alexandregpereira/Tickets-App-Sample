package com.example.cielo.event.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cielo.event.GetEventsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UiModel da listagem de eventos.
 *
 * A UI observa [state] e envia intenções chamando as funções públicas; efeitos únicos saem por
 * [actions].
 */
class EventListUiModel(
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

    /** A UI avisa que já tratou a última ação, liberando o replay. */
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
