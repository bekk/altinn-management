package no.kartverket.altinnmanagement.client.auth

internal fun interface AccessTokenProvider {
    suspend fun accessToken(): AccessToken
}
