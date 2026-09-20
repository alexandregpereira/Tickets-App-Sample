package com.example.shop

import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase

/** Resolves the event from the same catalog that feeds the list. */
internal class DefaultGetEventUseCase(
    private val getEvents: GetEventsUseCase,
) : GetEventUseCase {

    override suspend fun invoke(eventId: String): Event? =
        getEvents().firstOrNull { it.id == eventId }
}
