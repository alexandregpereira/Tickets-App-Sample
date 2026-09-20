package com.example.shop.core

/**
 * An event with tickets available for sale.
 *
 * @param priceInCents unit ticket price in cents — payments work in integer cents, so the app avoids
 * floating point across the whole chain of values.
 */
data class Event(
    val id: String,
    val name: String,
    val priceInCents: Long,
)
