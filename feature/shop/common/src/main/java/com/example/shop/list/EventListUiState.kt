package com.example.shop.list

import com.example.shop.core.Event

internal data class EventListUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val errorMessage: String? = null,
)

/** Efeitos únicos — navegação. Emitidos por SharedFlow para não serem reprocessados. */
internal sealed interface EventListUiAction {
    data class NavigateToCheckout(val eventId: String) : EventListUiAction
}
