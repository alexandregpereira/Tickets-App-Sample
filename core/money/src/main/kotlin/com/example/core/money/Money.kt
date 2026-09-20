package com.example.core.money

import java.text.NumberFormat
import java.util.Locale

private val brlFormat: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))

/** Formats cents as Brazilian currency: `12000` -> `R$ 120,00`. */
fun Long.formatAsBrl(): String = brlFormat.format(this / 100.0)
