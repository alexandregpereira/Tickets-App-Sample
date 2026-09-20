package com.example.payment.cielo

/**
 * Credentials for the Cielo Smart integration.
 *
 * Issued by the Cielo Developer Portal (https://desenvolvedores.cielo.com.br/api-portal/) when
 * registering an app with the "Cielo Smart - Order Manager" API.
 *
 * The values below are MOCKS and should be replaced with real credentials before transacting
 * against the emulator or a terminal. In a production app they would not be hardcoded in the
 * source: they would come from a backend or, at the very least, from `local.properties` via
 * BuildConfig.
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
