package com.example.payment.cielo

import com.example.payment.core.StartPaymentUseCase
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The only public thing in this module, alongside what it binds in `payment:core`.
 *
 * Swapping Cielo for another acquirer means replacing this Koin module: nothing outside it
 * references a `Cielo*` type, or even knows a `PaymentActivity` exists.
 */
val paymentModule = module {
    // Mocked credentials: replace with the values from the Cielo Developer Portal.
    single { CieloCredentials.MOCK }

    single<Base64Codec> { AndroidBase64Codec() }
    single { CieloDeepLinkBuilder(get()) }
    single { CieloResponseParser(get()) }
    single { CieloPaymentRequestFactory(get()) }
    single { CieloPaymentContract(get()) }
    // createdAtStart: it has to exist before the first Activity is created, or it misses the
    // lifecycle callbacks it relies on to reconnect in-flight payments.
    single(createdAtStart = true) { PaymentResultLauncher(androidApplication(), get()) }

    factory<StartPaymentUseCase> { CieloStartPaymentUseCase(get(), get(), get()) }

    viewModel { (paymentDeepLink: String) -> PaymentUiModel(paymentDeepLink) }
}
