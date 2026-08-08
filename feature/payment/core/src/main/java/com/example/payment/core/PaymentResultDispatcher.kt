package com.example.payment.core

import android.content.Intent

/**
 * Ponte entre a Activity que recebe o retorno do app de pagamento e o [PaymentResultSource].
 *
 * Existe para que a Activity não precise saber **como** o resultado vem codificado na Intent — esse
 * conhecimento é do módulo de implementação. A Activity só repassa o que o Android lhe entregou.
 */
interface PaymentResultDispatcher {

    /** @return `true` se a Intent era um retorno de pagamento e foi publicada. */
    fun dispatch(intent: Intent): Boolean
}
