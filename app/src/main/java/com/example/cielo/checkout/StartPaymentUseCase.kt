package com.example.cielo.checkout

import android.content.ActivityNotFoundException
import com.example.cielo.cielo.CieloCheckoutLauncher
import com.example.cielo.cielo.CieloCredentials
import com.example.cielo.cielo.CieloDeepLinkBuilder
import com.example.cielo.cielo.CieloPaymentCode
import com.example.cielo.cielo.CieloPaymentError
import com.example.cielo.cielo.CieloPaymentItem
import com.example.cielo.cielo.CieloPaymentRequest
import com.example.cielo.event.Event
import com.example.cielo.purchase.Purchase
import com.example.cielo.purchase.PurchaseRepository
import com.example.cielo.purchase.PurchaseStatus

/**
 * Registra a compra como pendente e abre o checkout da Cielo Smart.
 *
 * A compra é gravada **antes** de o deep link ser aberto: se o processo for morto enquanto o app da
 * Cielo está em foreground, ainda existe um registro pendente associado à `reference`, e o retorno
 * pode ser reconciliado sem gerar uma segunda cobrança.
 */
class StartPaymentUseCase(
    private val deepLinkBuilder: CieloDeepLinkBuilder,
    private val checkoutLauncher: CieloCheckoutLauncher,
    private val purchaseRepository: PurchaseRepository,
    private val credentials: CieloCredentials,
) {

    suspend operator fun invoke(
        reference: String,
        event: Event,
        quantity: Int,
        paymentCode: CieloPaymentCode,
    ): StartPaymentResult {
        val purchase = purchaseRepository.start(
            Purchase(
                reference = reference,
                eventId = event.id,
                eventName = event.name,
                quantity = quantity,
                unitPriceInCents = event.priceInCents,
                paymentCode = paymentCode,
                status = PurchaseStatus.PENDING,
            )
        )

        val request = CieloPaymentRequest(
            accessToken = credentials.accessToken,
            clientId = credentials.clientId,
            reference = purchase.reference,
            items = listOf(
                CieloPaymentItem(
                    name = "${event.name} - Ingresso",
                    quantity = purchase.quantity,
                    sku = event.id,
                    unitPrice = purchase.unitPriceInCents,
                )
            ),
            paymentCode = paymentCode.value,
            value = purchase.totalInCents.toString(),
        )

        return try {
            checkoutLauncher.launch(deepLinkBuilder.buildPaymentUri(request))
            StartPaymentResult.Launched(purchase)
        } catch (e: ActivityNotFoundException) {
            StartPaymentResult.Failed(
                error = CieloPaymentError.APP_NOT_FOUND,
                reason = "Cielo Smart não encontrada neste dispositivo. " +
                    "Instale o app da Cielo ou o Emulador Cielo para pagar.",
            )
        }
    }
}

sealed interface StartPaymentResult {
    /** Deep link aberto; o desfecho chegará depois via `order://response`. */
    data class Launched(val purchase: Purchase) : StartPaymentResult

    /** Falha local — o checkout da Cielo nem chegou a abrir, então não houve cobrança. */
    data class Failed(val error: CieloPaymentError, val reason: String) : StartPaymentResult
}
