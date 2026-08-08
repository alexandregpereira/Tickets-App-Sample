package com.example.checkout.purchase.receipt

import com.example.checkout.purchase.Purchase

internal data class ReceiptUiState(
    val purchase: Purchase? = null,
    /** A compra não foi encontrada — só acontece se o registro em memória tiver sido perdido. */
    val isMissing: Boolean = false,
)
