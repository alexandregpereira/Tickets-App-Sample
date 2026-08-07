package com.example.cielo.checkout

import com.example.cielo.cielo.CieloPaymentCode
import com.example.cielo.event.Event

data class CheckoutUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val quantity: Int = MIN_QUANTITY,
    val paymentCode: CieloPaymentCode = CieloPaymentCode.CREDITO_AVISTA,
    /** Verdadeiro entre abrir o checkout da Cielo e receber o `order://response`. */
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

sealed interface CheckoutUiAction {
    data class NavigateToReceipt(val purchaseReference: String) : CheckoutUiAction
}
