package com.example.payment.cielo

import com.example.payment.core.PaymentResultDispatcher
import com.example.payment.core.PaymentResultSource
import com.example.payment.core.StartPaymentUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Única coisa pública deste módulo, junto do que ele liga em `payment:core`.
 *
 * Trocar a Cielo por outra adquirente é substituir este módulo Koin: nada fora daqui referencia um
 * tipo `Cielo*`.
 */
val paymentModule = module {
    // Credenciais mockadas: substituir pelos valores do Portal de Desenvolvedores da Cielo.
    single { CieloCredentials.MOCK }

    single<Base64Codec> { AndroidBase64Codec() }
    single<CieloCheckoutLauncher> { AndroidCieloCheckoutLauncher(androidContext()) }
    single { CieloDeepLinkBuilder(get()) }
    single { CieloResponseParser(get()) }
    single { CieloResultBus() }

    single { CieloPaymentResultSource(get(), get()) }
    single<PaymentResultSource> { get<CieloPaymentResultSource>() }
    single<PaymentResultDispatcher> { get<CieloPaymentResultSource>() }

    factory<StartPaymentUseCase> { CieloStartPaymentUseCase(get(), get(), get()) }
}
