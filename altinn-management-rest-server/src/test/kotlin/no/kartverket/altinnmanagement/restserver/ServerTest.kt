package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServerTest {

    @Test
    fun `the liveness probe answers 200`() = testServer {
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }

    @Test
    fun `the spec is served as json, without the liveness probe and itself`() = testServer {
        val response = client.get("/openapi")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val spec = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val paths = spec["paths"]?.jsonObject?.keys.orEmpty()
        assertFalse("/health/live" in paths || "/openapi" in paths, "expected both to be hidden, got: $paths")
        assertFalse(spec.containsKey("servers"), "the host differs per environment, so the spec names none")
    }

    @Test
    fun `a path with no endpoint gives 404, of type not-found`() = testServer {
        val response = client.get("/health/alive")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
        assertEquals(
            ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no endpoint at this path"),
            response.errorResponse(),
        )
    }

    @Test
    fun `a method the endpoint does not accept gives 405, of type method-not-allowed`() = testServer {
        val response = client.post("/health/live")

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
        assertEquals(
            ErrorResponse.of(
                ProblemType.METHOD_NOT_ALLOWED,
                HttpStatusCode.MethodNotAllowed,
                "The endpoint at this path does not accept this method",
            ),
            response.errorResponse(),
        )
    }

    @Test
    fun `an unexpected exception gives 500, of type internal-error, without its message`() = testApplication {
        application {
            module(testResourceRegistryClient(), noFiltering, accessLog = false)
            routing { get("/fails") { error("internal detail") } }
        }

        val response = client.get("/fails")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
        assertEquals(
            """{"type":"tag:kartverket.no,2026:internal-error","title":"Internal error","status":500}""",
            response.bodyAsText(),
        )
    }
}
