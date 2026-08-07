package com.example.cielo.purchase.receipt

import androidx.lifecycle.ViewModel
import com.example.cielo.purchase.PurchaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UiModel do comprovante: apenas lê a compra já registrada pela `reference`. */
class ReceiptUiModel(
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
