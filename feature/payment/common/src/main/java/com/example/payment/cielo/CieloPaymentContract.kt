package com.example.payment.cielo

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult

/**
 * The Activity Result API contract for payments: a deep link goes in, a [PaymentResult] comes out.
 *
 * [PaymentActivity] returns the raw payload (the Base64 `response`) rather than a ready-made
 * [PaymentResult]. That way the result doesn't have to cross the Intent serialized, and translating
 * Cielo's format stays in one place — the [CieloResponseParser].
 */
internal class CieloPaymentContract(
    private val responseParser: CieloResponseParser,
) : ActivityResultContract<String, PaymentResult>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(context, PaymentActivity::class.java)
            .putExtra(PaymentActivity.EXTRA_PAYMENT_DEEP_LINK, input)
            // Reinforces what the theme already asks for: the bridge screen must not show up as a
            // transition.
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

    override fun parseResult(resultCode: Int, intent: Intent?): PaymentResult {
        val encodedResponse = intent?.getStringExtra(PaymentActivity.EXTRA_ENCODED_RESPONSE)
        if (resultCode == Activity.RESULT_OK && encodedResponse != null) {
            return responseParser.parse(encodedResponse)
        }

        val outcome = intent?.getStringExtra(PaymentActivity.EXTRA_OUTCOME)
            ?.let { name -> PaymentOutcome.entries.firstOrNull { it.name == name } }

        return when (outcome) {
            PaymentOutcome.APP_NOT_FOUND -> PaymentResult.Failed(
                error = PaymentError.APP_NOT_FOUND,
                reason = "Cielo Smart não encontrada neste dispositivo. " +
                    "Instale o app da Cielo ou o Emulador Cielo para pagar.",
            )

            PaymentOutcome.INVALID_RESPONSE -> PaymentResult.Failed(
                error = PaymentError.INVALID_RESPONSE,
                reason = "Não foi possível ler a resposta da Cielo.",
            )

            // Covers the case where the system finishes the screen without returning any extra.
            PaymentOutcome.ABANDONED, null -> PaymentResult.Failed(
                error = PaymentError.ABANDONED,
                reason = "Pagamento não concluído.",
            )
        }
    }
}
