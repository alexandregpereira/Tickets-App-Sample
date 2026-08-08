package com.example.payment.cielo

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Ponte entre a `MainActivity` (que recebe o deep link `order://response` em `onNewIntent`) e o
 * `CheckoutUiModel`, que é quem sabe interpretar o resultado.
 *
 * Usa `replay = 1` porque a Intent pode chegar antes de o UiModel começar a coletar — por exemplo
 * se o Android tiver recriado o processo enquanto o app da Cielo estava em foreground.
 *
 * O UiModel chama [consume] após tratar o resultado para que a mesma resposta não seja reprocessada
 * numa recomposição ou recriação de tela; ainda assim, a idempotência real fica no
 * `PurchaseRepository`.
 */
internal class CieloResultBus {

    private val _responses = MutableSharedFlow<String>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val responses: SharedFlow<String> = _responses.asSharedFlow()

    /** @param encodedResponse valor do query param `response` (JSON em Base64). */
    fun post(encodedResponse: String) {
        _responses.tryEmit(encodedResponse)
    }

    /**
     * Há um retorno publicado e ainda não consumido.
     *
     * Permite distinguir "a Cielo respondeu e o resultado está a caminho" de "o usuário saiu da
     * Cielo sem concluir", já que sair pelo botão voltar não gera nenhum callback.
     */
    fun hasPendingResponse(): Boolean = _responses.replayCache.isNotEmpty()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consume() {
        _responses.resetReplayCache()
    }
}
