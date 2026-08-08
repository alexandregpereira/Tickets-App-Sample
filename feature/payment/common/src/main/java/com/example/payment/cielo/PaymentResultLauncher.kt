package com.example.payment.cielo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.example.payment.core.PaymentResult
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference

/**
 * Abre a [PaymentActivity] e aguarda o desfecho, **sobrevivendo à recriação da Activity**.
 *
 * A Activity Result API exige um registro feito por uma Activity, mas quem pede o pagamento é um
 * UiModel, que não tem nenhuma. Rastrear a Activity aqui dentro mantém esse detalhe confinado ao
 * módulo de pagamento, sem obrigar a `MainActivity` a carregar encanação de pagamento.
 *
 * O ponto sutil é a rotação: a Activity hospedeira é destruída, e com ela o callback registrado —
 * mas o `ActivityResultRegistry` **guarda o resultado pendente** no estado salvo, e entrega-o assim
 * que a mesma chave for registrada de novo. Por isso todo pagamento em andamento é revinculado a
 * cada Activity nova: quem estava esperando recebe o desfecho, em vez de esperar para sempre.
 */
internal class PaymentResultLauncher(
    application: Application,
    private val contract: CieloPaymentContract,
) {

    /** Chave do pagamento → quem está aguardando o desfecho. */
    private val pending = mutableMapOf<String, CompletableDeferred<PaymentResult>>()
    private val launchers = mutableMapOf<String, MutableList<ActivityResultLauncher<String>>>()
    private val bindings = PaymentBindings()

    private var currentActivity: WeakReference<ComponentActivity>? = null

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    onActivityAvailable(activity)

                // Vinculamos em created **e** resumed: assim não dependemos de o registry já estar
                // restaurado no primeiro dos dois. [PaymentBindings] evita o registro duplicado.
                override fun onActivityResumed(activity: Activity) = onActivityAvailable(activity)

                override fun onActivityDestroyed(activity: Activity) {
                    bindings.forgetActivity(activity)
                    if (currentActivity?.get() === activity) currentActivity = null
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            }
        )
    }

    suspend fun launch(key: String, deepLink: String): PaymentResult? {
        val activity = currentActivity?.get() ?: return null

        val deferred = CompletableDeferred<PaymentResult>()
        pending[key] = deferred
        bind(activity, setOf(key))

        return try {
            launcherFor(key)?.launch(deepLink) ?: return null
            deferred.await()
        } finally {
            forget(key)
        }
    }

    private fun onActivityAvailable(activity: Activity) {
        // A própria ponte não serve de âncora: ela é quem vai embora quando o pagamento termina.
        if (activity !is ComponentActivity || activity is PaymentActivity) return
        currentActivity = WeakReference(activity)
        bind(activity, pending.keys.toSet())
    }

    private fun bind(activity: ComponentActivity, keys: Set<String>) {
        bindings.keysToBind(activity, keys).forEach { key ->
            // Registrar a chave entrega na hora um resultado que o sistema tenha guardado — é o que
            // faz o pagamento voltar depois de uma rotação.
            val launcher = activity.activityResultRegistry.register(key, contract) { result ->
                pending[key]?.complete(result)
            }
            launchers.getOrPut(key) { mutableListOf() } += launcher
        }
    }

    private fun launcherFor(key: String): ActivityResultLauncher<String>? =
        launchers[key]?.lastOrNull()

    private fun forget(key: String) {
        pending -= key
        launchers.remove(key)?.forEach { it.unregister() }
        bindings.forget(key)
    }
}
