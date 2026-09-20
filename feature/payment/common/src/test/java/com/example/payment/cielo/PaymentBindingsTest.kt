package com.example.payment.cielo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that makes a payment survive Activity recreation: rebind on the new instance, without
 * ever registering the same key twice on the same one.
 */
class PaymentBindingsTest {

    private val bindings = PaymentBindings()
    private val activity = Any()
    private val recreatedActivity = Any()

    @Test
    fun `binds a key that is not bound yet`() {
        assertEquals(setOf("pagamento-1"), bindings.keysToBind(activity, setOf("pagamento-1")))
    }

    @Test
    fun `does not bind the same key twice on the same activity`() {
        bindings.keysToBind(activity, setOf("pagamento-1"))

        assertEquals(emptySet<String>(), bindings.keysToBind(activity, setOf("pagamento-1")))
    }

    @Test
    fun `binds only what is missing`() {
        bindings.keysToBind(activity, setOf("pagamento-1"))

        val toBind = bindings.keysToBind(activity, setOf("pagamento-1", "pagamento-2"))

        assertEquals(setOf("pagamento-2"), toBind)
    }

    @Test
    fun `binds again on a recreated activity`() {
        bindings.keysToBind(activity, setOf("pagamento-1"))

        // This is what saves rotation: the new instance has to register the key again, so the
        // registry delivers the result it had stored.
        assertEquals(
            setOf("pagamento-1"),
            bindings.keysToBind(recreatedActivity, setOf("pagamento-1")),
        )
    }

    @Test
    fun `a finished payment can be bound again later`() {
        bindings.keysToBind(activity, setOf("pagamento-1"))

        bindings.forget("pagamento-1")

        assertEquals(setOf("pagamento-1"), bindings.keysToBind(activity, setOf("pagamento-1")))
    }

    @Test
    fun `a destroyed activity stops holding bindings`() {
        bindings.keysToBind(activity, setOf("pagamento-1"))

        bindings.forgetActivity(activity)

        assertEquals(setOf("pagamento-1"), bindings.keysToBind(activity, setOf("pagamento-1")))
    }
}
