package com.example.cielo.event

/**
 * Evento disponível para venda de ingressos.
 *
 * @param priceInCents preço unitário do ingresso em centavos — a Cielo trabalha com inteiros em
 * centavos, então o app evita ponto flutuante em toda a cadeia de valores.
 */
data class Event(
    val id: String,
    val name: String,
    val priceInCents: Long,
)
