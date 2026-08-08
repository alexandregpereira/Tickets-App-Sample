package com.example.payment.cielo

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A regra mais delicada do fluxo: separar "a Cielo respondeu" de "o usuário voltou sem concluir".
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

        // Recriação da Activity: o UiModel sobrevive e não pode reabrir um pagamento em andamento.
        uiModel.onScreenStart()
        uiModel.onScreenStart()

        uiModel.actions.test {
            assertEquals(PaymentUiAction.OpenDeepLink(deepLink), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `does not conclude abandonment on the resume that follows the launch`() = runTest {
        val uiModel = PaymentUiModel(deepLink)
        uiModel.onScreenStart()

        // Ainda não passamos por onPause: a Cielo nem apareceu.
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

        // O app de pagamento troca de tela: resumimos por um instante e saímos de cena de novo.
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

        // O onResume vem logo depois do onNewIntent: não pode virar desistência.
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
