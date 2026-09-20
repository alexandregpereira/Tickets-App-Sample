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
 * Drives the round trip to the Cielo Smart app on behalf of [PaymentActivity].
 *
 * The hard rule here is telling **"Cielo replied"** apart from **"the user came back without
 * finishing"**, since the latter produces no callback at all. The distinction is made through the
 * lifecycle, not through timing: we only conclude a dropout on an `onResume` that comes **after**
 * the screen has been paused — that is, after the Cielo app has actually shown up. Without that
 * condition, the `onResume` following `onCreate` itself would end the payment before it began.
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
     * The deep link is opened exactly once: the UiModel survives Activity recreation, and
     * relaunching would open Cielo again for a payment that is already in flight.
     */
    fun onScreenStart() {
        if (hasLaunched) return
        hasLaunched = true
        _actions.tryEmit(PaymentUiAction.OpenDeepLink(paymentDeepLink))
    }

    /**
     * The screen signals it has handled the last action, releasing the replay.
     *
     * Without this, [hasLaunched] would guard only the **emission**, not the **delivery**: the
     * `replay = 1` exists so an outcome emitted with no collector isn't lost, but it would also
     * redeliver the already-consumed `OpenDeepLink` to every new Activity — and rotating the screen
     * inside Cielo would open the payment app a second time.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun onActionHandled() = _actions.resetReplayCache()

    fun onScreenPause() {
        if (hasLaunched) hasLeftScreen = true
        // We left the foreground again: it wasn't a dropout.
        abandonJob?.cancel()
        abandonJob = null
    }

    /**
     * We are back in the foreground with no response — the user possibly backed out.
     *
     * "Possibly" because the Cielo app switches screens during the flow (its own feedback screen,
     * for instance), and during those transitions this Activity briefly resumes before losing focus
     * again. Concluding a dropout on the first `onResume` was killing payments that were going
     * through. That is why a dropout only counts if the foreground **holds**: any `onPause` or
     * response from the acquirer before that cancels the conclusion.
     */
    fun onScreenResume() {
        if (!hasLeftScreen || isFinished) return
        abandonJob?.cancel()
        abandonJob = viewModelScope.launch {
            delay(ABANDON_GRACE)
            finishWith(PaymentUiAction.FinishWithError(PaymentOutcome.ABANDONED))
        }
    }

    /** Cielo replied on the `order://response` contract. */
    fun onDeepLinkResult(deepLink: String) {
        val response = CieloCallbackUri.encodedResponseOrNull(deepLink)
            ?: return finishWith(PaymentUiAction.FinishWithError(PaymentOutcome.INVALID_RESPONSE))
        finishWith(PaymentUiAction.FinishWithResponse(response))
    }

    /** There is no app able to handle the payment deep link. */
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
         * How long the screen must stay in the foreground, with no response, for a dropout to
         * count. Short enough not to feel like a freeze, long enough to absorb the payment app's
         * screen transitions.
         */
        val ABANDON_GRACE = 1.seconds
    }
}

internal sealed interface PaymentUiAction {
    data class OpenDeepLink(val uri: String) : PaymentUiAction
    data class FinishWithResponse(val encodedResponse: String) : PaymentUiAction
    data class FinishWithError(val outcome: PaymentOutcome) : PaymentUiAction
}

/** Outcomes [PaymentActivity] decides on its own, without a response from the acquirer. */
internal enum class PaymentOutcome {
    ABANDONED,
    APP_NOT_FOUND,
    INVALID_RESPONSE,
}
