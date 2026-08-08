package com.example.shop.core

/**
 * Recupera um único evento pelo id.
 *
 * Contrato exposto pela loja para as demais features: o checkout precisa saber o que está vendendo,
 * mas não precisa — e não deve — enxergar de onde vem o catálogo.
 */
fun interface GetEventUseCase {

    suspend operator fun invoke(eventId: String): Event?
}
