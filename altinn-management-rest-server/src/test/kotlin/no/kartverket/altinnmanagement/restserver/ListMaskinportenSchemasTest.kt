package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.header
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
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListMaskinportenSchemasTest {

    private val sentToAltinn = mutableListOf<OutgoingRequest>()
    private val sentToFiltering = mutableListOf<OutgoingRequest>()

    private val certificate = "Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api"

    private fun altinn(status: Int = 200, body: String = "[${resourceJson()}]") = OutgoingHttpClient { request ->
        sentToAltinn += request
        response(status, "application/json; charset=utf-8", body)
    }

    private fun filtering(answer: () -> OutgoingResponse) = ExternalFiltering(
        ExternalFilteringClient(
            URI("https://filtering.test/altinn"),
            OutgoingHttpClient { request ->
                sentToFiltering += request
                answer()
            },
        ),
    )

    private fun filteringAllowing(scopes: String, resources: String) = filtering {
        response(200, "application/json", """{"result":{"scopes":$scopes,"resources":$resources}}""")
    }

    private fun resource(id: String, vararg scopes: String) = """
        {
          "identifier": "$id",
          "resourceType": "MaskinportenSchema",
          "resourceReferences": [${scopes.joinToString { """{"reference":"$it","referenceType":"MaskinportenScope"}""" }}]
        }
    """.trimIndent()

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
    fun `lists only the resources the calling app has access to, along with every one of their scopes`() = testServer(
        altinn = altinn(
            body = listOf(
                resource("res-exact", "kartverk:dokumentbestilling"),
                resource("res-prefix-demo", "kartverk:tilgangsstyring:demo.read", "kartverk:dokumentbestilling"),
                resource("res-prefix-other", "kartverk:tilgangsstyring:demo.read", "kartverk:matrikkel:brukernavn"),
                resource("kv-matrikkel-brukernavn", "kartverk:dokumentbestilling"),
            ).joinToString(prefix = "[", postfix = "]"),
        ),
        filtering = filteringAllowing(
            scopes = """{"exact":["kartverk:dokumentbestilling"],"prefix":["kartverk:tilgangsstyring:"]}""",
            resources = """{"exact":["res-exact"],"prefix":["res-prefix-"]}""",
        ),
    ) {
        val response = client.get("/api/maskinportenschemas") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("res-exact", "res-prefix-demo"), response.maskinportenSchemas().map { it.resourceId })
        assertContains(sentToFiltering.single().body!!.decodeToString(), "matrikkel-api")
    }

    @Test
    fun `answers an empty list when the calling app has access to nothing`() = testServer(altinn(), filtering { response(200, "application/json", "{}") }) {
        val response = client.get("/api/maskinportenschemas") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `gives 400 when filtering is on and X-Forwarded-Client-Cert is missing, without asking anyone`() =
        testServer(altinn(), filteringAllowing(scopes = """{"exact":[],"prefix":[]}""", resources = """{"exact":[],"prefix":[]}""")) {
            for (value in listOf(null, " ")) {
                client.get("/api/maskinportenschemas") { value?.let { header(CLIENT_CERTIFICATE_HEADER, it) } }.assertProblem(
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
                    ),
                )
            }

            assertTrue(sentToAltinn.isEmpty(), "Altinn was called: $sentToAltinn")
            assertTrue(sentToFiltering.isEmpty(), "The filtering service was called: $sentToFiltering")
        }

    @Test
    fun `gives 502 when the filtering service fails, without asking Altinn`() =
        testServer(altinn(), filtering { response(503, "text/plain", "unavailable") }) {
            client.get("/api/maskinportenschemas") { header(CLIENT_CERTIFICATE_HEADER, certificate) }.assertProblem(
                ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed"),
            )

            assertTrue(sentToAltinn.isEmpty(), "Altinn was called: $sentToAltinn")
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
