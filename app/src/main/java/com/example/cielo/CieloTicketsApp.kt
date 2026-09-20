package com.example.cielo

import android.app.Application
import com.example.checkout.checkoutModule
import com.example.payment.cielo.paymentModule
import com.example.shop.shopModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Composition root of the app.
 *
 * The only place that knows every module at once — and the only one that mentions Cielo by name.
 * Switching acquirers means swapping `paymentModule` for another implementation of
 * `feature:payment:core` right here.
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
