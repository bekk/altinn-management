package no.kartverket.altinnmanagement.restserver

import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.time.Clock
import java.time.Duration

internal fun Application.externalFiltering(config: AppConfig): ExternalFiltering {
    val url = config.externalFilteringUrl
    if (url == null) {
        log.warn("External filtering is off")
        return ExternalFiltering(null)
    }
    return ExternalFiltering(ExternalFilteringClient(url, httpClient()), config.externalFilteringCacheDuration)
}

class ExternalFiltering(
    private val client: ExternalFilteringClient?,
    cacheDuration: Duration = Duration.ZERO,
    clock: Clock = Clock.systemUTC(),
) {
    private val cache = AccessFilterCache(cacheDuration, clock)

    val isOn: Boolean get() = client != null

    suspend fun filterFor(certificate: String?): AccessFilter? {
        val client = client ?: return null
        checkNotNull(certificate)
        cache.get(certificate)?.let { return it }
        return client.filterFor(certificate).also { cache.put(certificate, it) }
    }
}
