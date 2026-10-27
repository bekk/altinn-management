package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
}
