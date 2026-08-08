package com.example.checkout

import com.example.shop.core.Event

internal data class CheckoutUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val quantity: Int = MIN_QUANTITY,
    /** Verdadeiro entre abrir o app de pagamento e receber o desfecho. */
    val isPaymentInFlight: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalInCents: Long = (event?.priceInCents ?: 0L) * quantity

    val canDecreaseQuantity: Boolean = quantity > MIN_QUANTITY && !isPaymentInFlight
    val canIncreaseQuantity: Boolean = quantity < MAX_QUANTITY && !isPaymentInFlight

    /** Gate visual da prevenção de cobrança duplicada; o UiModel repete a checagem. */
    val canPay: Boolean = event != null && !isPaymentInFlight

    companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 10
    }
}

internal sealed interface CheckoutUiAction {
    data class NavigateToReceipt(val purchaseReference: String) : CheckoutUiAction
}
