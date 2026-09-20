package com.example.checkout

import com.example.shop.core.Event

internal data class CheckoutUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val quantity: Int = MIN_QUANTITY,
    /** True between opening the payment app and receiving the outcome. */
    val isPaymentInFlight: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalInCents: Long = (event?.priceInCents ?: 0L) * quantity

    val canDecreaseQuantity: Boolean = quantity > MIN_QUANTITY && !isPaymentInFlight
    val canIncreaseQuantity: Boolean = quantity < MAX_QUANTITY && !isPaymentInFlight

    /** Visual gate for duplicate charge prevention; the UiModel repeats the check. */
    val canPay: Boolean = event != null && !isPaymentInFlight

    companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 10
    }
}

internal sealed interface CheckoutUiAction {
    /**
     * @param isApproved lets navigation decide whether the checkout still belongs on the stack: on
     * a completed purchase there is nothing to go back to, but on a decline the user must be able to
     * try again.
     */
    data class NavigateToReceipt(
        val purchaseReference: String,
        val isApproved: Boolean,
    ) : CheckoutUiAction
}
