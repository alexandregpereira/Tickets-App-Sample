package com.example.payment.cielo

/**
 * Credenciais de integração com a Cielo Smart.
 *
 * Obtidas no Portal de Desenvolvedores da Cielo (https://desenvolvedores.cielo.com.br/api-portal/)
 * ao cadastrar um aplicativo com a API "Cielo Smart - Order Manager".
 *
 * Os valores abaixo são MOCKS e devem ser substituídos pelas credenciais reais antes de
 * transacionar contra o emulador ou um terminal. Em um app de produção elas não ficariam
 * hardcoded no fonte: viriam de um backend ou, no mínimo, de `local.properties` via BuildConfig.
 */
internal data class CieloCredentials(
    val clientId: String,
    val accessToken: String,
) {
    companion object {
        val MOCK = CieloCredentials(
            clientId = "MOCK_CLIENT_ID",
            accessToken = "MOCK_ACCESS_TOKEN",
        )
    }
}
