package no.kartverket.altinnmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.log
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.client.filtering.Access
import no.kartverket.altinnmanagement.client.filtering.ExternalFilteringClient
import no.kartverket.altinnmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

/** Kartverket's organisation number, which owns the resources this API manages. */
internal const val KARTVERKET = "971040238"

internal fun resourceRegistryClient(config: AppConfig): ResourceRegistryClient =
    ResourceRegistryClient(config.altinnEnvironment.platformBaseUrl, httpClient())

internal fun Application.externalFiltering(config: AppConfig): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient()))
}

private fun httpClient(): OutgoingHttpClient = LoggingOutgoingHttpClient(
    JavaOutgoingHttpClient(
        HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        REQUEST_TIMEOUT,
    ),
)

class ExternalFiltering(private val client: ExternalFilteringClient?) {
    val isOn: Boolean get() = client != null

    /** What the calling app may see, or null when filtering is off. */
    suspend fun accessFor(certificate: String?): Access? {
        val client = client ?: return null
        return client.accessFor(checkNotNull(certificate))
    }
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
