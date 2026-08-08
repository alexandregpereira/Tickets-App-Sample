package com.example.payment.cielo

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentResult

/**
 * Contrato da Activity Result API para o pagamento: entra um deep link, sai um [PaymentResult].
 *
 * A [PaymentActivity] devolve o payload cru (o `response` em Base64) em vez de um [PaymentResult]
 * pronto. Assim o resultado não precisa atravessar a Intent serializado, e a tradução do formato da
 * Cielo continua num lugar só — o [CieloResponseParser].
 */
internal class CieloPaymentContract(
    private val responseParser: CieloResponseParser,
) : ActivityResultContract<String, PaymentResult>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(context, PaymentActivity::class.java)
            .putExtra(PaymentActivity.EXTRA_PAYMENT_DEEP_LINK, input)
            // Reforça o que o tema já pede: a tela-ponte não deve aparecer como uma transição.
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

            // Inclui o caso de a tela ser encerrada pelo sistema sem devolver extra nenhum.
            PaymentOutcome.ABANDONED, null -> PaymentResult.Failed(
                error = PaymentError.ABANDONED,
                reason = "Pagamento não concluído.",
            )
        }
    }
}
