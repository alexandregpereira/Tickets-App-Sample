package com.example.checkout

import com.example.checkout.purchase.PurchaseRepository
import com.example.checkout.purchase.receipt.ReceiptUiModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val checkoutModule = module {
    // Singleton: guarda as compras em memória para toda a sessão do app.
    single { PurchaseRepository() }

    viewModel { (eventId: String) ->
        CheckoutUiModel(
            eventId = eventId,
            getEvent = get(),
            startPayment = get(),
            purchaseRepository = get(),
        )
    }
    viewModel { (purchaseReference: String) -> ReceiptUiModel(purchaseReference, get()) }
}
