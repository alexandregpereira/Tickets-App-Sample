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
 * UiModel do checkout: escolhe a quantidade, dispara o pagamento e registra o desfecho.
 *
 * Não conhece adquirente nenhuma — fala só com `payment:core`, e o pagamento é uma única chamada que
 * suspende até terminar. Trocar o meio de pagamento não muda uma linha deste arquivo.
 *
 * Prevenção de cobrança duplicada, requisito explícito do case:
 * 1. [onPayClick] é ignorado enquanto houver um pagamento em andamento, então nunca há dois pedidos
 *    abertos ao mesmo tempo;
 * 2. cada tentativa tem a sua própria `reference` e a sua própria compra; uma tentativa que não
 *    chegou a cobrar é descartada por inteiro;
 * 3. o [PurchaseRepository] ignora escritas sobre uma compra já finalizada e se recusa a descartá-la.
 */
internal class CheckoutUiModel(
    private val eventId: String,
    private val getEvent: GetEventUseCase,
    private val startPayment: StartPaymentUseCase,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    // replay = 1: o pagamento acontece em outra tela, então a ação de navegação pode ser emitida com
    // esta em STOPPED. A UI confirma o consumo em [onActionHandled] para não renavegar depois.
    private val _actions = MutableSharedFlow<CheckoutUiAction>(replay = 1)
    val actions: SharedFlow<CheckoutUiAction> = _actions.asSharedFlow()

    /** Chave de idempotência da tentativa em curso. */
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

    /** A UI avisa que já tratou a última ação, liberando o replay. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onActionHandled() = _actions.resetReplayCache()

    fun onPayClick() {
        val current = _state.value
        // Barreira contra double tap / reenvio da ação: um pagamento por vez.
        if (!current.canPay) return
        val event = current.event ?: return

        // Toda tentativa começa do zero: se a anterior foi abandonada, a compra dela é descartada
        // aqui, para o pedido enviado refletir o que está na tela agora.
        abandonPendingPurchase()
        val reference = UUID.randomUUID().toString()
        pendingReference = reference

        // A compra é gravada **antes** de abrir o pagamento: se o processo for morto enquanto o app
        // de pagamento está em foreground, ainda existe um registro ligado à `reference`.
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
            // A cobrança nem chegou a ser tentada: não há desfecho para registrar nem comprovante a
            // exibir. Desistir é silencioso; a falta do app de pagamento precisa ser explicada.
            abandonPendingPurchase()
            if (result.error != PaymentError.ABANDONED) {
                _state.update { it.copy(errorMessage = result.reason) }
            }
            return
        }

        purchaseRepository.recordResult(reference, result)
        pendingReference = null

        // Aprovada, negada ou cancelada, o desfecho é registrado e mostrado no comprovante — a
        // recusa inclusive, que o case exige registrar e exibir.
        _actions.emit(
            CheckoutUiAction.NavigateToReceipt(
                purchaseReference = reference,
                isApproved = result is PaymentResult.Approved,
            )
        )
    }

    /**
     * Erros em que a adquirente sequer chegou a ser acionada.
     *
     * `GENERIC` fica de fora de propósito: é uma falha real vinda da adquirente, que precisa virar
     * compra recusada e comprovante como qualquer outra.
     */
    private val PaymentError.isNotACharge: Boolean
        get() = this == PaymentError.ABANDONED || this == PaymentError.APP_NOT_FOUND

    /**
     * Descarta a compra de uma tentativa que não se concretizou e zera a `reference`, para que a
     * tentativa seguinte parta do que está na tela.
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
