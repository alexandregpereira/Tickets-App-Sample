package com.example.payment.cielo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Conduz a ida e volta até o app da Cielo Smart, para a [PaymentActivity].
 *
 * A regra difícil aqui é distinguir **"a Cielo respondeu"** de **"o usuário voltou sem concluir"**,
 * já que a segunda não gera callback algum. A distinção é feita por ciclo de vida, não por tempo:
 * só concluímos desistência num `onResume` que venha **depois** de a tela ter sido pausada — ou
 * seja, depois de o app da Cielo realmente ter aparecido. Sem essa condição, o `onResume` que segue
 * o próprio `onCreate` encerraria o pagamento antes de ele começar.
 */
internal class PaymentUiModel(
    private val paymentDeepLink: String,
) : ViewModel() {

    private val _actions = MutableSharedFlow<PaymentUiAction>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<PaymentUiAction> = _actions.asSharedFlow()

    private var hasLaunched = false
    private var hasLeftScreen = false
    private var isFinished = false
    private var abandonJob: Job? = null

    /**
     * O deep link é aberto uma única vez: em recriação da Activity o UiModel sobrevive, e relançar
     * abriria a Cielo de novo para um pagamento que já está em andamento.
     */
    fun onScreenStart() {
        if (hasLaunched) return
        hasLaunched = true
        _actions.tryEmit(PaymentUiAction.OpenDeepLink(paymentDeepLink))
    }

    fun onScreenPause() {
        if (hasLaunched) hasLeftScreen = true
        // Voltamos a sair de cena: não era desistência.
        abandonJob?.cancel()
        abandonJob = null
    }

    /**
     * Voltamos ao primeiro plano sem retorno — possivelmente o usuário desistiu.
     *
     * "Possivelmente" porque o app da Cielo troca de tela durante o fluxo (a tela de feedback dele,
     * por exemplo), e nessas transições esta Activity chega a resumir por um instante antes de
     * perder o foco de novo. Concluir desistência no primeiro `onResume` matava pagamentos que
     * estavam dando certo. Por isso a desistência só vale se o primeiro plano **se sustentar**:
     * qualquer `onPause` ou retorno da adquirente antes disso cancela a conclusão.
     */
    fun onScreenResume() {
        if (!hasLeftScreen || isFinished) return
        abandonJob?.cancel()
        abandonJob = viewModelScope.launch {
            delay(ABANDON_GRACE)
            finishWith(PaymentUiAction.FinishWithError(PaymentOutcome.ABANDONED))
        }
    }

    /** A Cielo respondeu no contrato `order://response`. */
    fun onDeepLinkResult(deepLink: String) {
        val response = CieloCallbackUri.encodedResponseOrNull(deepLink)
            ?: return finishWith(PaymentUiAction.FinishWithError(PaymentOutcome.INVALID_RESPONSE))
        finishWith(PaymentUiAction.FinishWithResponse(response))
    }

    /** Não há app capaz de atender ao deep link de pagamento. */
    fun onLaunchFailed() {
        finishWith(PaymentUiAction.FinishWithError(PaymentOutcome.APP_NOT_FOUND))
    }

    private fun finishWith(action: PaymentUiAction) {
        if (isFinished) return
        isFinished = true
        abandonJob?.cancel()
        abandonJob = null
        _actions.tryEmit(action)
    }

    private companion object {
        /**
         * Tempo que a tela precisa ficar em primeiro plano, sem retorno, para valer desistência.
         * Curto o bastante para não parecer travamento, longo o bastante para absorver as trocas de
         * tela do app de pagamento.
         */
        val ABANDON_GRACE = 1.seconds
    }
}

internal sealed interface PaymentUiAction {
    data class OpenDeepLink(val uri: String) : PaymentUiAction
    data class FinishWithResponse(val encodedResponse: String) : PaymentUiAction
    data class FinishWithError(val outcome: PaymentOutcome) : PaymentUiAction
}

/** Desfechos que a própria [PaymentActivity] determina, sem resposta da adquirente. */
internal enum class PaymentOutcome {
    ABANDONED,
    APP_NOT_FOUND,
    INVALID_RESPONSE,
}
