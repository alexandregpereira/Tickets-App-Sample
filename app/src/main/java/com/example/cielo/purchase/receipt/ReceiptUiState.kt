package com.example.cielo.purchase.receipt

import com.example.cielo.purchase.Purchase

data class ReceiptUiState(
    val purchase: Purchase? = null,
    /** A compra não foi encontrada — só acontece se o registro em memória tiver sido perdido. */
    val isMissing: Boolean = false,
)
