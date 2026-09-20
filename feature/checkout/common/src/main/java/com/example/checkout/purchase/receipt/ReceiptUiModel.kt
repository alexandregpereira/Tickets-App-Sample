package com.example.checkout.purchase.receipt

import androidx.lifecycle.ViewModel
import com.example.checkout.purchase.PurchaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The receipt UiModel: it only reads the purchase already recorded under the `reference`. */
internal class ReceiptUiModel(
    private val purchaseReference: String,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptUiState())
    val state: StateFlow<ReceiptUiState> = _state.asStateFlow()

    init {
        val purchase = purchaseRepository.find(purchaseReference)
        _state.value = ReceiptUiState(purchase = purchase, isMissing = purchase == null)
    }
}
