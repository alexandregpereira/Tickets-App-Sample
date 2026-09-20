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
 * Opens [PaymentActivity] and awaits the outcome, **surviving Activity recreation**.
 *
 * The Activity Result API requires a registration made by an Activity, but the one asking to pay is
 * a UiModel, which has none. Tracking the Activity in here keeps that detail confined to the payment
 * module, without forcing `MainActivity` to carry payment plumbing.
 *
 * The subtle part is rotation: the host Activity is destroyed, and with it the registered callback —
 * but `ActivityResultRegistry` **keeps the pending result** in the saved state, and delivers it as
 * soon as the same key is registered again. That is why every in-flight payment is rebound to each
 * new Activity: whoever was waiting gets the outcome, instead of waiting forever.
 */
internal class PaymentResultLauncher(
    application: Application,
    private val contract: CieloPaymentContract,
) {

    /** Payment key → whoever is awaiting the outcome. */
    private val pending = mutableMapOf<String, CompletableDeferred<PaymentResult>>()
    private val launchers = mutableMapOf<String, MutableList<ActivityResultLauncher<String>>>()
    private val bindings = PaymentBindings()

    private var currentActivity: WeakReference<ComponentActivity>? = null

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    onActivityAvailable(activity)

                // We bind on created **and** resumed: that way we don't depend on the registry
                // already being restored at the first of the two. [PaymentBindings] prevents the
                // duplicate registration.
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
        // The bridge itself is no anchor: it is the one that goes away when the payment ends.
        if (activity !is ComponentActivity || activity is PaymentActivity) return
        currentActivity = WeakReference(activity)
        bind(activity, pending.keys.toSet())
    }

    private fun bind(activity: ComponentActivity, keys: Set<String>) {
        bindings.keysToBind(activity, keys).forEach { key ->
            // Registering the key immediately delivers a result the system may have stored — which
            // is what brings the payment back after a rotation.
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
