package com.example.shop

import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase

/** Resolve o evento a partir do mesmo catálogo que alimenta a listagem. */
internal class DefaultGetEventUseCase(
    private val getEvents: GetEventsUseCase,
) : GetEventUseCase {

    override suspend fun invoke(eventId: String): Event? =
        getEvents().firstOrNull { it.id == eventId }
}
