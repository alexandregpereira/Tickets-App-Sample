package com.example.checkout.purchase.receipt

import com.example.checkout.purchase.Purchase

internal data class ReceiptUiState(
    val purchase: Purchase? = null,
    /** The purchase was not found — this only happens if the in-memory record was lost. */
    val isMissing: Boolean = false,
)
