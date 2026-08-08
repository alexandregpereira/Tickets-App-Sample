package com.example.payment.cielo

import android.content.ActivityNotFoundException
import com.example.payment.core.PaymentError
import com.example.payment.core.PaymentOrder
import com.example.payment.core.StartPaymentResult
import com.example.payment.core.StartPaymentUseCase

/**
 * Abre o checkout da Cielo Smart para um [PaymentOrder].
 *
 * Traduz o pedido genérico para o JSON da Cielo, converte para Base64 e dispara o deep link. É o
 * único ponto do app onde um pedido vira uma requisição de adquirente.
 */
internal class CieloStartPaymentUseCase(
    private val deepLinkBuilder: CieloDeepLinkBuilder,
    private val checkoutLauncher: CieloCheckoutLauncher,
    private val credentials: CieloCredentials,
) : StartPaymentUseCase {

    override suspend fun invoke(order: PaymentOrder): StartPaymentResult {
        val request = CieloPaymentRequest(
            accessToken = credentials.accessToken,
            clientId = credentials.clientId,
            reference = order.reference,
            items = order.items.map { item ->
                CieloPaymentItem(
                    name = item.name,
                    quantity = item.quantity,
                    sku = item.sku,
                    unitPrice = item.unitPriceInCents,
                )
            },
            value = order.totalInCents.toString(),
        )

        return try {
            checkoutLauncher.launch(deepLinkBuilder.buildPaymentUri(request))
            StartPaymentResult.Launched
        } catch (e: ActivityNotFoundException) {
            StartPaymentResult.Failed(
                error = PaymentError.APP_NOT_FOUND,
                reason = "Cielo Smart não encontrada neste dispositivo. " +
                    "Instale o app da Cielo ou o Emulador Cielo para pagar.",
            )
        }
    }
}
