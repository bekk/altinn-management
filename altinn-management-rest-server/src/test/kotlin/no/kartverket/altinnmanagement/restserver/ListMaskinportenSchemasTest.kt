package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ListMaskinportenSchemasTest {

    private val sentToAltinn = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 200, body: String = "[${resourceJson()}]") = OutgoingHttpClient { request ->
        sentToAltinn += request
        response(status, "application/json; charset=utf-8", body)
    }

    private val upstreamError = ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed")

    private suspend fun HttpResponse.maskinportenSchemas(): List<MaskinportenSchema> =
        Json.decodeFromString(ListSerializer(MaskinportenSchema.serializer()), bodyAsText())

    private suspend fun HttpResponse.assertProblem(expected: ErrorResponse) {
        assertEquals(expected.status, status.value)
        assertEquals(ContentType.Application.ProblemJson, contentType()?.withoutParameters())
        assertEquals(expected, errorResponse())
    }

    @Test
    fun `lists the MaskinportenSchemas in this API's model, the same as getting each one`() = testServer({ request ->
        response(200, "application/json", if (request.url.path.endsWith("/search")) "[${resourceJson()}]" else resourceJson())
    }) {
        val response = client.get("/api/maskinportenschemas")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val expected = Json.decodeFromString(MaskinportenSchema.serializer(), client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").bodyAsText())
        assertEquals(listOf(expected), response.maskinportenSchemas())
    }

    @Test
    fun `asks Altinn's search for Kartverket's MaskinportenSchemas, with a GET and without a token`() = testServer(altinn()) {
        client.get("/api/maskinportenschemas")

        val request = sentToAltinn.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(
            "https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/search?organizationId=971040238&resourceType=MaskinportenSchema",
            request.url.toString(),
        )
        assertEquals(mapOf("Accept" to "application/json"), request.headers)
    }

    @Test
    fun `answers an empty list when Altinn has none`() = testServer(altinn(body = "[]")) {
        val response = client.get("/api/maskinportenschemas")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `gives 502 when Altinn answers with an error`() = testServer(altinn(status = 500, body = "boom")) {
        client.get("/api/maskinportenschemas").assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn answers with something that is not a list of resources`() {
        for (body in listOf("{}", """[{"resourceType":"MaskinportenSchema"}]""", """[{"identifier":"Not an id"}]""")) {
            testServer(altinn(body = body)) {
                client.get("/api/maskinportenschemas").assertProblem(upstreamError)
            }
        }
    }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/maskinportenschemas")

        response.assertProblem(upstreamError)
        assertFalse("Connection refused" in response.bodyAsText())
    }
}
