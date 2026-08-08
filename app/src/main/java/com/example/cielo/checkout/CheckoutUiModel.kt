package com.example.cielo.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cielo.cielo.CieloPaymentCode
import com.example.cielo.cielo.CieloResponseParser
import com.example.cielo.cielo.CieloResultBus
import com.example.cielo.event.GetEventUseCase
import com.example.cielo.purchase.PurchaseRepository
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
 * UiModel do checkout: escolhe quantidade e meio de pagamento, dispara o deep link da Cielo Smart e
 * reconcilia o retorno.
 *
 * Prevenção de cobrança duplicada, requisito explícito do case:
 * 1. [pendingReference] é gerada **uma única vez** por compra e reutilizada nas retentativas, então
 *    a Cielo enxerga sempre o mesmo pedido lógico;
 * 2. [onPayClick] é ignorado enquanto houver um pagamento em andamento;
 * 3. o [PurchaseRepository] ignora escritas sobre uma compra já finalizada, protegendo contra
 *    entregas repetidas da mesma Intent de resposta.
 */
class CheckoutUiModel(
    private val eventId: String,
    private val getEvent: GetEventUseCase,
    private val startPayment: StartPaymentUseCase,
    private val purchaseRepository: PurchaseRepository,
    private val responseParser: CieloResponseParser,
    private val resultBus: CieloResultBus,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    // replay = 1: o retorno da Cielo costuma chegar com esta tela em STOPPED (o app da Cielo estava
    // em foreground). Sem replay, a ação de navegação seria emitida sem coletor e se perderia.
    // A UI confirma o consumo em [onActionHandled] para não renavegar a cada recoleta.
    private val _actions = MutableSharedFlow<CheckoutUiAction>(replay = 1)
    val actions: SharedFlow<CheckoutUiAction> = _actions.asSharedFlow()

    /** Chave de idempotência da compra em curso. Sobrevive a retentativas; só zera após concluir. */
    private var pendingReference: String? = null

    init {
        loadEvent()
        observeCieloResponses()
    }

    fun onIncreaseQuantityClick() = _state.update {
        if (it.canIncreaseQuantity) it.copy(quantity = it.quantity + 1) else it
    }

    fun onDecreaseQuantityClick() = _state.update {
        if (it.canDecreaseQuantity) it.copy(quantity = it.quantity - 1) else it
    }

    fun onPaymentCodeSelect(paymentCode: CieloPaymentCode) = _state.update {
        if (it.isPaymentInFlight) it else it.copy(paymentCode = paymentCode)
    }

    fun onErrorDismiss() = _state.update { it.copy(errorMessage = null) }

    /**
     * A tela voltou ao foreground.
     *
     * Sair da Cielo Smart pelo botão voltar não gera callback algum: sem isto, a tela ficaria presa
     * em "aguardando pagamento" para sempre, com o botão de pagar desabilitado. Se houver um retorno
     * publicado, ele está a caminho e quem decide é [observeCieloResponses]; caso contrário, o
     * usuário desistiu e a tela é liberada para uma nova tentativa.
     *
     * A [pendingReference] é mantida de propósito: uma nova tentativa reaproveita a mesma chave de
     * idempotência, então uma eventual cobrança que tenha ocorrido não se duplica.
     */
    fun onScreenResume() {
        if (!_state.value.isPaymentInFlight) return
        if (resultBus.hasPendingResponse()) return
        _state.update { it.copy(isPaymentInFlight = false) }
    }

    /** A UI avisa que já tratou a última ação, liberando o replay. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onActionHandled() = _actions.resetReplayCache()

    fun onPayClick() {
        val current = _state.value
        // Barreira contra double tap / reenvio da ação: um pagamento por vez.
        if (!current.canPay) return
        val event = current.event ?: return

        val reference = pendingReference ?: UUID.randomUUID().toString().also {
            pendingReference = it
        }

        _state.update { it.copy(isPaymentInFlight = true, errorMessage = null) }
        viewModelScope.launch {
            val result = startPayment(
                reference = reference,
                event = event,
                quantity = current.quantity,
                paymentCode = current.paymentCode,
            )
            if (result is StartPaymentResult.Failed) {
                // O checkout da Cielo não abriu, então não houve cobrança: liberamos nova tentativa
                // reaproveitando a mesma reference.
                _state.update { it.copy(isPaymentInFlight = false, errorMessage = result.reason) }
            }
        }
    }

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

    private fun observeCieloResponses() {
        viewModelScope.launch {
            resultBus.responses.collect { encodedResponse ->
                // Consome sempre, inclusive um retorno sem compra correspondente: deixá-lo no cache
                // faria [onScreenResume] achar para sempre que há um desfecho a caminho.
                resultBus.consume()
                val reference = pendingReference ?: return@collect

                val result = responseParser.parse(encodedResponse)
                purchaseRepository.recordResult(reference, result)
                pendingReference = null
                _state.update { it.copy(isPaymentInFlight = false) }

                // Aprovada, negada ou cancelada, o desfecho é registrado e mostrado no comprovante
                // — inclusive a recusa, que o case exige registrar e exibir.
                _actions.emit(CheckoutUiAction.NavigateToReceipt(reference))
            }
        }
    }
}
