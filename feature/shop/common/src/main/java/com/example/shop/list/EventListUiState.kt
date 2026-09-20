package com.example.shop.list

import com.example.shop.core.Event

internal data class EventListUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val errorMessage: String? = null,
)

/** One-shot effects — navigation. Emitted through a SharedFlow so they aren't reprocessed. */
internal sealed interface EventListUiAction {
    data class NavigateToCheckout(val eventId: String) : EventListUiAction
}
