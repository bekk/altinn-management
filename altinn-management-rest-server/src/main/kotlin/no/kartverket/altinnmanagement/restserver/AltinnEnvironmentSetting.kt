package no.kartverket.altinnmanagement.restserver

import no.kartverket.altinnmanagement.client.AltinnEnvironment
import java.net.URI

// LOCAL is the mock API in mock-api, which plays both Altinn and Maskinporten. The client library has no environment
// for it, so the server gives the client the mock's URLs
internal enum class AltinnEnvironmentSetting(val platformBaseUrl: URI, val maskinportenTokenUrl: URI) {
    TT02(AltinnEnvironment.TT02),
    PROD(AltinnEnvironment.PROD),
    LOCAL(
        platformBaseUrl = URI("http://localhost:8090"),
        maskinportenTokenUrl = URI("http://localhost:8090/token"),
    ),
    ;

    constructor(environment: AltinnEnvironment) : this(environment.platformBaseUrl, environment.maskinportenTokenUrl)
}
