package com.example.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkout.purchase.Purchase
import com.example.checkout.purchase.PurchaseRepository
import com.example.checkout.purchase.PurchaseStatus
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentItem
import com.example.payment.core.PaymentOrder
import com.example.payment.core.PaymentResult
import com.example.payment.core.StartPaymentUseCase
import com.example.shop.core.Event
import com.example.shop.core.GetEventUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The checkout UiModel: picks the quantity, triggers the payment and records the outcome.
 *
 * It knows no acquirer — it only talks to `payment:core`, and paying is a single call that suspends
 * until it finishes. Switching payment providers doesn't change a line of this file.
 *
 * Duplicate charge prevention:
 * 1. [onPayClick] is ignored while a payment is in flight, so there are never two open orders at
 *    the same time;
 * 2. each attempt has its own `reference` and its own purchase; an attempt that never charged is
 *    discarded entirely;
 * 3. the [PurchaseRepository] ignores writes over an already finalized purchase and refuses to
 *    discard it.
 */
internal class CheckoutUiModel(
    private val eventId: String,
    private val getEvent: GetEventUseCase,
    private val startPayment: StartPaymentUseCase,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    // replay = 1: payment happens on another screen, so the navigation action can be emitted while
    // this one is STOPPED. The UI acknowledges it in [onActionHandled] to avoid renavigating later.
    private val _actions = MutableSharedFlow<CheckoutUiAction>(replay = 1)
    val actions: SharedFlow<CheckoutUiAction> = _actions.asSharedFlow()

    /** Idempotency key of the attempt in progress. */
    private var pendingReference: String? = null

    init {
        loadEvent()
    }

    fun onIncreaseQuantityClick() = _state.update {
        if (it.canIncreaseQuantity) it.copy(quantity = it.quantity + 1) else it
    }

    fun onDecreaseQuantityClick() = _state.update {
        if (it.canDecreaseQuantity) it.copy(quantity = it.quantity - 1) else it
    }

    fun onErrorDismiss() = _state.update { it.copy(errorMessage = null) }

    /** The UI signals it has handled the last action, releasing the replay. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onActionHandled() = _actions.resetReplayCache()

    fun onPayClick() {
        val current = _state.value
        // Barrier against a double tap / resent action: one payment at a time.
        if (!current.canPay) return
        val event = current.event ?: return

        // Every attempt starts from scratch: if the previous one was abandoned, its purchase is
        // discarded here, so the order we send reflects what is on screen now.
        abandonPendingPurchase()
        val reference = UUID.randomUUID().toString()
        pendingReference = reference

        // The purchase is written **before** opening the payment: if the process is killed while
        // the payment app is in the foreground, a record tied to the `reference` still exists.
        val purchase = purchaseRepository.start(
            Purchase(
                reference = reference,
                eventId = event.id,
                eventName = event.name,
                quantity = current.quantity,
                unitPriceInCents = event.priceInCents,
                status = PurchaseStatus.PENDING,
            )
        )

        _state.update { it.copy(isPaymentInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            handlePaymentResult(reference, startPayment(purchase.toPaymentOrder(event)))
        }
    }

    private suspend fun handlePaymentResult(reference: String, result: PaymentResult) {
        _state.update { it.copy(isPaymentInFlight = false) }

        if (result is PaymentResult.Failed && result.error.isNotACharge) {
            // The charge was never even attempted: there is no outcome to record and no receipt to
            // show. Backing out is silent; a missing payment app has to be explained.
            abandonPendingPurchase()
            if (result.error != PaymentError.ABANDONED) {
                _state.update { it.copy(errorMessage = result.reason) }
            }
            return
        }

        purchaseRepository.recordResult(reference, result)
        pendingReference = null

        // Approved, denied or cancelled, the outcome is recorded and shown on the receipt — the
        // decline included.
        _actions.emit(
            CheckoutUiAction.NavigateToReceipt(
                purchaseReference = reference,
                isApproved = result is PaymentResult.Approved,
            )
        )
    }

    /**
     * Errors where the acquirer was never even reached.
     *
     * `GENERIC` is deliberately left out: it is a real failure coming from the acquirer, and has to
     * become a declined purchase and a receipt like any other.
     */
    private val PaymentError.isNotACharge: Boolean
        get() = this == PaymentError.ABANDONED || this == PaymentError.APP_NOT_FOUND

    /**
     * Discards the purchase of an attempt that never went through and clears the `reference`, so
     * the next attempt starts from what is on screen.
     */
    private fun abandonPendingPurchase() {
        pendingReference?.let(purchaseRepository::discard)
        pendingReference = null
    }

    private fun Purchase.toPaymentOrder(event: Event) = PaymentOrder(
        reference = reference,
        totalInCents = totalInCents,
        items = listOf(
            PaymentItem(
                sku = event.id,
                name = "${event.name} - Ingresso",
                quantity = quantity,
                unitPriceInCents = unitPriceInCents,
            )
        ),
    )

    private fun loadEvent() {
        viewModelScope.launch {
            val event = runCatching { getEvent(eventId) }.getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    event = event,
                    errorMessage = if (event == null) "Evento não encontrado." else it.errorMessage,
                )
            }
        }
    }
}
