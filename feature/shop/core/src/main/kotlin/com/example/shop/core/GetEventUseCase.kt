package com.example.shop.core

/**
 * Retrieves a single event by id.
 *
 * The contract the shop exposes to the other features: the checkout needs to know what it is
 * selling, but it doesn't need — and shouldn't have — visibility into where the catalog comes from.
 */
fun interface GetEventUseCase {

    suspend operator fun invoke(eventId: String): Event?
}
