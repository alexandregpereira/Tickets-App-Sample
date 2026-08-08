package com.example.shop.core

/**
 * Evento disponível para venda de ingressos.
 *
 * @param priceInCents preço unitário do ingresso em centavos — pagamento trabalha com inteiros em
 * centavos, então o app evita ponto flutuante em toda a cadeia de valores.
 */
data class Event(
    val id: String,
    val name: String,
    val priceInCents: Long,
)
