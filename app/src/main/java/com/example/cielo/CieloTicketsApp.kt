package com.example.cielo

import android.app.Application
import com.example.checkout.checkoutModule
import com.example.payment.cielo.paymentModule
import com.example.shop.shopModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Raiz de composição do app.
 *
 * É o único lugar que conhece todos os módulos ao mesmo tempo — e o único que menciona a Cielo por
 * nome. Trocar a adquirente significa trocar `paymentModule` por outra implementação de
 * `feature:payment:core` aqui.
 */
class CieloTicketsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CieloTicketsApp)
            modules(shopModule, checkoutModule, paymentModule)
        }
    }
}
