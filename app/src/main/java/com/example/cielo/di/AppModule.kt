package com.example.cielo.di

import com.example.cielo.checkout.CheckoutUiModel
import com.example.cielo.checkout.StartPaymentUseCase
import com.example.cielo.cielo.AndroidCieloCheckoutLauncher
import com.example.cielo.cielo.AndroidBase64Codec
import com.example.cielo.cielo.Base64Codec
import com.example.cielo.cielo.CieloCheckoutLauncher
import com.example.cielo.cielo.CieloCredentials
import com.example.cielo.cielo.CieloDeepLinkBuilder
import com.example.cielo.cielo.CieloResponseParser
import com.example.cielo.cielo.CieloResultBus
import com.example.cielo.event.GetEventUseCase
import com.example.cielo.event.GetEventsUseCase
import com.example.cielo.event.list.EventListUiModel
import com.example.cielo.purchase.PurchaseRepository
import com.example.cielo.purchase.receipt.ReceiptUiModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Credenciais mockadas: substituir pelos valores do Portal de Desenvolvedores da Cielo.
    single { CieloCredentials.MOCK }

    single<Base64Codec> { AndroidBase64Codec() }
    single<CieloCheckoutLauncher> { AndroidCieloCheckoutLauncher(androidContext()) }
    single { CieloDeepLinkBuilder(get()) }
    single { CieloResponseParser(get()) }
    // Singleton: a MainActivity publica o retorno do deep link e o CheckoutUiModel consome.
    single { CieloResultBus() }

    // Singleton: guarda as compras em memória para toda a sessão do app.
    single { PurchaseRepository() }

    factory { GetEventsUseCase() }
    factory { GetEventUseCase(get()) }
    factory { StartPaymentUseCase(get(), get(), get(), get()) }

    viewModel { EventListUiModel(get()) }
    viewModel { (eventId: String) ->
        CheckoutUiModel(
            eventId = eventId,
            getEvent = get(),
            startPayment = get(),
            purchaseRepository = get(),
            responseParser = get(),
            resultBus = get(),
        )
    }
    viewModel { (purchaseReference: String) -> ReceiptUiModel(purchaseReference, get()) }
}
