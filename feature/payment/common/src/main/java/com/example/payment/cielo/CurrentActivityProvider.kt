package com.example.payment.cielo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * Guarda a `ComponentActivity` em primeiro plano.
 *
 * A Activity Result API exige um registro feito por uma Activity, mas quem pede o pagamento é um
 * UiModel, que não tem nenhuma. Rastrear a Activity atual aqui dentro é o que permite manter esse
 * detalhe confinado ao módulo de pagamento, sem obrigar a `MainActivity` a carregar encanação de
 * pagamento.
 *
 * Limitação assumida: o registro acontece no momento do pagamento, então se a Activity hospedeira
 * for recriada enquanto a Cielo está em primeiro plano (rotação, por exemplo), o resultado se perde
 * e o checkout fica aguardando.
 */
internal class CurrentActivityProvider(application: Application) {

    private var current: WeakReference<ComponentActivity>? = null

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (activity is ComponentActivity) current = WeakReference(activity)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (current?.get() === activity) current = null
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
        )
    }

    fun current(): ComponentActivity? = current?.get()
}
