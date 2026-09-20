package com.example.shop

import com.example.shop.core.Event
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Returns the events available for purchase.
 *
 * The list is mocked: this project is about the payment flow, not about building a backend. The
 * `delay` simulates network latency so the UI's loading states are actually exercised.
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
