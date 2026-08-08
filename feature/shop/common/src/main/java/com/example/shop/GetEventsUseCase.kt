package com.example.shop

import com.example.shop.core.Event
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Devolve os eventos disponíveis para compra.
 *
 * A lista é mockada: o case explicita que a construção de um backend de apoio não é avaliada. O
 * `delay` simula latência de rede para que os estados de carregamento da UI sejam exercitados.
 */
internal class GetEventsUseCase {

    suspend operator fun invoke(): List<Event> {
        delay(FAKE_NETWORK_DELAY_MS.milliseconds)
        return MOCK_EVENTS
    }

    private companion object {
        const val FAKE_NETWORK_DELAY_MS = 600L

        val MOCK_EVENTS = listOf(
            Event(id = "evt-1", name = "Festival de Verão", priceInCents = 12_000),
            Event(id = "evt-2", name = "Show de Rock na Praça", priceInCents = 8_500),
            Event(id = "evt-3", name = "Stand-up Comedy Night", priceInCents = 6_000),
            Event(id = "evt-4", name = "Teatro: O Auto da Compadecida", priceInCents = 4_500),
            Event(id = "evt-5", name = "Roda de Samba", priceInCents = 3_000),
            Event(id = "evt-6", name = "Cinema ao Ar Livre", priceInCents = 2_500),
        )
    }
}
