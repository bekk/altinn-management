package no.kartverket.altinnmanagement.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.AltinnEnvironment
import no.kartverket.altinnmanagement.client.ResourceRegistryClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse

internal fun testServer(
    altinn: OutgoingHttpClient = unexpectedCall,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(testResourceRegistryClient(altinn)) }
    block()
}

internal fun testResourceRegistryClient(httpClient: OutgoingHttpClient = unexpectedCall) =
    ResourceRegistryClient(AltinnEnvironment.TT02.platformBaseUrl, httpClient)

private val unexpectedCall = OutgoingHttpClient { error("unexpected call to ${it.url}") }

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())
