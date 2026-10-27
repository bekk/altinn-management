package no.kartverket.altinnmanagement.restserver

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse

internal fun testServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module() }
    block()
}

internal suspend fun HttpResponse.errorResponse(): ErrorResponse =
    Json.decodeFromString(ErrorResponse.serializer(), bodyAsText())
