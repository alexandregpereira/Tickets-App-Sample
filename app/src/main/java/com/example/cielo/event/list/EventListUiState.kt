package com.example.cielo.event.list

import com.example.cielo.event.Event

data class EventListUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val errorMessage: String? = null,
)

/** Efeitos únicos — navegação. Emitidos por SharedFlow para não serem reprocessados. */
sealed interface EventListUiAction {
    data class NavigateToCheckout(val eventId: String) : EventListUiAction
}
