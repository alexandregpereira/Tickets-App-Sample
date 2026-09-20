package com.example.payment.cielo

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The most delicate rule in the flow: separating "Cielo replied" from "the user came back without
 * finishing".
 */
class PaymentUiModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val deepLink = "lio://payment?request=abc&urlCallback=order://response"

    @Test
    fun `opens the payment deep link when the screen starts`() = runTest {
        val uiModel = PaymentUiModel(deepLink)

        uiModel.actions.test {
            uiModel.onScreenStart()

            assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem())
        }
    }

    @Test
    fun `opens the deep link only once across screen restarts`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        // Activity recreation: the UiModel survives and must not reopen an in-flight payment.
        uiModel.onScreenStart()
        uiModel.onScreenStart()

        uiModel.actions.test {
            assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `does not reopen the payment app when the screen is recreated`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        // The screen opened the payment app and acknowledged consumption.
        uiModel.actions.test { assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem()) }
        uiModel.onActionHandled()

        // Rotating inside Cielo recreates the Activity: the UiModel survives and a new collector
        // subscribes to the flow. Without the acknowledgment above, replay would reopen the payment
        // app.
        uiModel.onScreenStart()

        uiModel.actions.test { expectNoEvents() }
    }

    @Test
    fun `does not conclude abandonment on the resume that follows the launch`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        // We haven't been through onPause yet: Cielo never even showed up.
        uiModel.onScreenResume()
        advanceUntilIdle()

        uiModel.actions.test {
            assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `concludes abandonment when returning without a result`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()
        uiModel.onScreenPause()

        uiModel.onScreenResume()
        advanceUntilIdle()

        uiModel.actions.test {
            assertEquals(
                PaymentUiAction.FinishWithError(PaymentOutcome.ABANDONED),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a brief resume between acquirer screens is not abandonment`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()
        uiModel.onScreenPause()

        // The payment app switches screens: we resume for an instant and leave again.
        uiModel.onScreenResume()
        uiModel.onScreenPause()
        advanceUntilIdle()

        uiModel.actions.test {
            assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `returns the encoded response when the acquirer replies`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()
        uiModel.onScreenPause()

        uiModel.onDeepLinkResult("order://response?response=eyJjb2RlIjoxfQ==&responsecode=0")

        uiModel.actions.test {
            assertEquals(
                PaymentUiAction.FinishWithResponse("eyJjb2RlIjoxfQ=="),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a result already received wins over a later resume`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()
        uiModel.onScreenPause()
        uiModel.onDeepLinkResult("order://response?response=payload")

        // onResume comes right after onNewIntent: it must not become a dropout.
        uiModel.onScreenResume()
        advanceUntilIdle()

        uiModel.actions.test {
            assertEquals(PaymentUiAction.FinishWithResponse("payload"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `reports an unreadable callback`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        uiModel.onDeepLinkResult("order://response?semParametro=1")

        uiModel.actions.test {
            assertEquals(
                PaymentUiAction.FinishWithError(PaymentOutcome.INVALID_RESPONSE),
                awaitItem(),
            )
        }
    }

    @Test
    fun `reports a missing payment app`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        uiModel.onLaunchFailed()

        uiModel.actions.test {
            assertEquals(
                PaymentUiAction.FinishWithError(PaymentOutcome.APP_NOT_FOUND),
                awaitItem(),
            )
        }
    }
}
