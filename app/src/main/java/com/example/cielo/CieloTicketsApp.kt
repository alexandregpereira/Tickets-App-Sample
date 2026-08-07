package com.example.cielo

import android.app.Application
import com.example.cielo.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CieloTicketsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CieloTicketsApp)
            modules(appModule)
        }
    }
}
