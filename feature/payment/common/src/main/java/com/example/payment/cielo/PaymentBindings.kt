package com.example.payment.cielo

/**
 * Tracks which payments are already bound to which Activity.
 *
 * The hard part of surviving Activity recreation isn't registering again — it is registering
 * **exactly once per instance**: the same payment has to be rebound on a new Activity, but never
 * twice on the same one, or the registry would end up with two subscriptions for one key.
 *
 * The rule is isolated here, free of Android types, so it can be covered by JVM tests.
 */
internal class PaymentBindings {

    /** Activity identity → keys already bound to it. */
    private val bound = mutableMapOf<Any, MutableSet<String>>()

    /**
     * Marks [keys] as bound to [activity] and returns only the ones that were **not** already.
     *
     * @return the keys the caller has to register now.
     */
    fun keysToBind(activity: Any, keys: Set<String>): Set<String> {
        val alreadyBound = bound.getOrPut(activity) { mutableSetOf() }
        val missing = keys - alreadyBound
        alreadyBound += missing
        return missing
    }

    /** The payment is over: drop it from every Activity. */
    fun forget(key: String) {
        bound.values.forEach { it -= key }
    }

    /** The Activity was destroyed: there is nothing left to bind to it. */
    fun forgetActivity(activity: Any) {
        bound -= activity
    }
}
