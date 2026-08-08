package com.example.payment.cielo

import com.example.payment.core.StartPaymentUseCase
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Única coisa pública deste módulo, junto do que ele liga em `payment:core`.
 *
 * Trocar a Cielo por outra adquirente é substituir este módulo Koin: nada fora daqui referencia um
 * tipo `Cielo*`, nem sabe que existe uma `PaymentActivity`.
 */
val paymentModule = module {
    // Credenciais mockadas: substituir pelos valores do Portal de Desenvolvedores da Cielo.
    single { CieloCredentials.MOCK }

    single<Base64Codec> { AndroidBase64Codec() }
    single { CieloDeepLinkBuilder(get()) }
    single { CieloResponseParser(get()) }
    single { CieloPaymentRequestFactory(get()) }
    single { CieloPaymentContract(get()) }
    // createdAtStart: precisa existir antes da primeira Activity ser resumida, senão perde o
    // callback e não teria quem registrar o launcher no momento do pagamento.
    single(createdAtStart = true) { CurrentActivityProvider(androidApplication()) }

    factory<StartPaymentUseCase> { CieloStartPaymentUseCase(get(), get(), get(), get()) }

    viewModel { (paymentDeepLink: String) -> PaymentUiModel(paymentDeepLink) }
}
