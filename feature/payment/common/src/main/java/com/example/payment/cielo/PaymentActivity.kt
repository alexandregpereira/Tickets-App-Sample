package com.example.payment.cielo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * The payment bridge screen: it opens the Cielo Smart app, receives `order://response` and returns
 * the outcome through the Activity Result API.
 *
 * It draws nothing (translucent theme) — it exists to own a lifecycle and an `intent-filter` of its
 * own. That is what lets the rest of the app treat payment as a call that suspends and returns a
 * result, with no event bus and no `onNewIntent` scattered across the main Activity.
 */
internal class PaymentActivity : ComponentActivity() {

    private val uiModel: PaymentUiModel by viewModel {
        parametersOf(intent.getStringExtra(EXTRA_PAYMENT_DEEP_LINK).orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Same system bar handling as the screen behind it, so bringing this Activity to the front
        // doesn't shift the layout or make the bars come and go.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_PAYMENT_DEEP_LINK).isNullOrBlank()) {
            // Instance created with no request — for example, an `order://response` that arrived
            // after this screen had already finished. Nobody is waiting for a result: just leave.
            finish()
            return
        }
        observeActions()
    }

    override fun onStart() {
        super.onStart()
        uiModel.onScreenStart()
    }

    override fun onResume() {
        super.onResume()
        uiModel.onScreenResume()
    }

    override fun onPause() {
        super.onPause()
        uiModel.onScreenPause()
    }

    /** Cielo's response lands here: this Activity owns the `order://response` contract. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLink = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString() ?: return
        uiModel.onDeepLinkResult(deepLink)
    }

    private fun observeActions() {
        // Collect for as long as the Activity exists, paused included: the outcome may arrive
        // before we are back in the foreground.
        lifecycleScope.launch {
            uiModel.actions.collect { action ->
                when (action) {
                    is PaymentUiAction.OpenDeepLink -> openCieloApp(action.uri)
                    is PaymentUiAction.FinishWithResponse -> finishWithResponse(action.encodedResponse)
                    is PaymentUiAction.FinishWithError -> finishWithError(action.outcome)
                }
                // Acknowledge consumption: a recreated Activity must not receive again what this
                // one already handled — the request to open the payment app in particular.
                uiModel.onActionHandled()
            }
        }
    }

    /**
     * Deliberately without `FLAG_ACTIVITY_NEW_TASK`: Cielo opens in the same task, so its back
     * button returns here — which is what makes dropout detection reliable.
     */
    private fun openCieloApp(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (e: ActivityNotFoundException) {
            uiModel.onLaunchFailed()
        }
    }

    private fun finishWithResponse(encodedResponse: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ENCODED_RESPONSE, encodedResponse))
        finish()
    }

    private fun finishWithError(outcome: PaymentOutcome) {
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_OUTCOME, outcome.name))
        finish()
    }

    companion object {
        /** The `lio://payment?...` deep link this screen should open. */
        const val EXTRA_PAYMENT_DEEP_LINK = "payment_deep_link"

        /** Base64 content of the `response` parameter returned by Cielo. */
        const val EXTRA_ENCODED_RESPONSE = "encoded_response"

        /** Name of a [PaymentOutcome], when the outcome was decided here and not by the acquirer. */
        const val EXTRA_OUTCOME = "outcome"
    }
}
