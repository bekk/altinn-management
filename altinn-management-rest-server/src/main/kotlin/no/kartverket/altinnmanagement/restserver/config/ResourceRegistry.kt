package no.kartverket.altinnmanagement.restserver.config

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.client.auth.MaskinportenAltinnTokenProvider
import no.kartverket.altinnmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFilteringClient
import no.kartverket.altinnmanagement.restserver.observability.LoggingOutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

internal fun resourceRegistryClient(config: AppConfig): ResourceRegistryClient {
    val httpClient = httpClient()
    val tokenProvider = MaskinportenAltinnTokenProvider(
        maskinportenTokenUrl = config.altinnEnvironment.maskinportenTokenUrl,
        clientId = config.maskinportenClientId,
        key = config.maskinportenKey,
        platformBaseUrl = config.altinnEnvironment.platformBaseUrl,
        httpClient = httpClient,
    )
    return ResourceRegistryClient(config.altinnEnvironment.platformBaseUrl, httpClient, tokenProvider)
}

internal fun Application.externalFiltering(config: AppConfig): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient()), config.externalFilteringCacheDuration)
}

internal fun httpClient(): OutgoingHttpClient = LoggingOutgoingHttpClient(
    JavaOutgoingHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
