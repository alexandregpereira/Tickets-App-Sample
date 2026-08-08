package com.example.payment.cielo

/**
 * Controla quais pagamentos já estão vinculados a qual Activity.
 *
 * A parte difícil de sobreviver à recriação da Activity não é registrar de novo — é registrar
 * **exatamente uma vez por instância**: o mesmo pagamento precisa ser revinculado numa Activity
 * nova, mas nunca duas vezes na mesma, ou o registry acabaria com duas inscrições da mesma chave.
 *
 * A regra fica isolada aqui, sem tipos do Android, para poder ser coberta por teste de JVM.
 */
internal class PaymentBindings {

    /** Identidade da Activity → chaves já vinculadas nela. */
    private val bound = mutableMapOf<Any, MutableSet<String>>()

    /**
     * Marca [keys] como vinculadas a [activity] e devolve só as que ainda **não** estavam.
     *
     * @return as chaves que o chamador precisa registrar agora.
     */
    fun keysToBind(activity: Any, keys: Set<String>): Set<String> {
        val alreadyBound = bound.getOrPut(activity) { mutableSetOf() }
        val missing = keys - alreadyBound
        alreadyBound += missing
        return missing
    }

    /** O pagamento terminou: some com ele de todas as Activities. */
    fun forget(key: String) {
        bound.values.forEach { it -= key }
    }

    /** A Activity foi destruída: nada mais a vincular nela. */
    fun forgetActivity(activity: Any) {
        bound -= activity
    }
}
