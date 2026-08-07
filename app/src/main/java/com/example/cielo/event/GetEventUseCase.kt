package com.example.cielo.event

/** Recupera um único evento pelo id, usado pela tela de checkout. */
class GetEventUseCase(
    private val getEvents: GetEventsUseCase,
) {

    suspend operator fun invoke(eventId: String): Event? =
        getEvents().firstOrNull { it.id == eventId }
}
