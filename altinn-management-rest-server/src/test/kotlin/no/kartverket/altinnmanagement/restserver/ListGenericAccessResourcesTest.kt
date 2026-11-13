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
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListGenericAccessResourcesTest {

    private val sentToAltinn = mutableListOf<OutgoingRequest>()
    private val sentToFiltering = mutableListOf<OutgoingRequest>()

    private val certificate = "Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api"

    private fun altinn(status: Int = 200, body: String = "[${resourceJson(GENERIC_ACCESS_RESOURCE)}]") = OutgoingHttpClient { request ->
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
          "resourceType": "$GENERIC_ACCESS_RESOURCE",
          "resourceReferences": [${scopes.joinToString { """{"reference":"$it","referenceType":"MaskinportenScope"}""" }}]
        }
    """.trimIndent()

    private val upstreamError = ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed")

    private suspend fun HttpResponse.genericAccessResources(): List<GenericAccessResource> =
        Json.decodeFromString(ListSerializer(GenericAccessResource.serializer()), bodyAsText())

    private suspend fun HttpResponse.assertProblem(expected: ErrorResponse) {
        assertEquals(expected.status, status.value)
        assertEquals(ContentType.Application.ProblemJson, contentType()?.withoutParameters())
        assertEquals(expected, errorResponse())
    }

    @Test
    fun `lists the GenericAccessResources in this API's model, without scopes`() = testServer(altinn()) {
        val response = client.get("/api/genericaccessresources")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val expected = GenericAccessResource(
            resourceId = "kv-matrikkel-brukernavn",
            title = MultilingualText(en = "Cadastre API", nb = "Matrikkel API", nn = "Matrikkel API"),
            description = MultilingualText(
                en = "Machine-to-machine access to the Cadastre.",
                nb = "Maskinell tilgang til Matrikkelen.",
                nn = "Maskinell tilgang til Matrikkelen.",
            ),
            delegationDescription = MultilingualText(
                en = "Allows a system provider to use the Matrikkel API on behalf of your organisation.",
                nb = "Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din.",
                nn = "Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di.",
            ),
            homepage = "https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen",
            contactPoints = listOf(
                ContactPoint(category = "Kartverket Kundesenter", email = "kundesenter@kartverket.no", telephone = "", contactPage = ""),
            ),
            keywords = listOf(Keyword(word = "matrikkel", language = "nb"), Keyword(word = "cadastre", language = "en")),
            status = "Completed",
            delegable = true,
            visible = true,
        )
        assertEquals(listOf(expected), response.genericAccessResources())
        assertFalse("scopes" in response.bodyAsText())
    }

    @Test
    fun `asks Altinn's search for Kartverket's GenericAccessResources, with a GET and without a token`() = testServer(altinn()) {
        client.get("/api/genericaccessresources")

        val request = sentToAltinn.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals(
            "https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/search?organizationId=971040238&resourceType=GenericAccessResource",
            request.url.toString(),
        )
        assertEquals(mapOf("Accept" to "application/json"), request.headers)
    }

    @Test
    fun `answers an empty list when Altinn has none`() = testServer(altinn(body = "[]")) {
        val response = client.get("/api/genericaccessresources")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `lists only the resources the calling app has access to, whatever scopes it may use`() = testServer(
        altinn = altinn(
            body = listOf(
                resource("res-exact"),
                resource("res-prefix-demo", "kartverk:matrikkel:brukernavn"),
                resource("res-exact-2"),
                resource("kv-matrikkel-brukernavn"),
            ).joinToString(prefix = "[", postfix = "]"),
        ),
        filtering = filteringAllowing(
            scopes = """{"exact":[],"prefix":[]}""",
            resources = """{"exact":["res-exact"],"prefix":["res-prefix-"]}""",
        ),
    ) {
        val response = client.get("/api/genericaccessresources") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("res-exact", "res-prefix-demo"), response.genericAccessResources().map { it.resourceId })
        assertContains(sentToFiltering.single().body!!.decodeToString(), "matrikkel-api")
    }

    @Test
    fun `answers an empty list when the calling app has access to nothing`() = testServer(altinn(), filtering { response(200, "application/json", "{}") }) {
        val response = client.get("/api/genericaccessresources") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun `ignores a scope parameter, since these resources have no scopes`() = testServer(altinn(body = "[${resource("res-a")}]")) {
        for (query in listOf("scope=kartverk%3Aa", "scope=", "scope=a&scope=b")) {
            val response = client.get("/api/genericaccessresources?$query")

            assertEquals(HttpStatusCode.OK, response.status, query)
            assertEquals(listOf("res-a"), response.genericAccessResources().map { it.resourceId }, query)
        }
    }

    @Test
    fun `gives 400 when filtering is on and X-Forwarded-Client-Cert is missing, without asking anyone`() =
        testServer(altinn(), filteringAllowing(scopes = """{"exact":[],"prefix":[]}""", resources = """{"exact":[],"prefix":[]}""")) {
            for (value in listOf(null, " ")) {
                client.get("/api/genericaccessresources") { value?.let { header(CLIENT_CERTIFICATE_HEADER, it) } }.assertProblem(
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
            client.get("/api/genericaccessresources") { header(CLIENT_CERTIFICATE_HEADER, certificate) }.assertProblem(
                ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed"),
            )

            assertTrue(sentToAltinn.isEmpty(), "Altinn was called: $sentToAltinn")
        }

    @Test
    fun `gives 502 when Altinn answers with an error`() = testServer(altinn(status = 500, body = "boom")) {
        client.get("/api/genericaccessresources").assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn answers with something that is not a list of resources`() {
        for (body in listOf("{}", """[{"resourceType":"$GENERIC_ACCESS_RESOURCE"}]""", """[{"identifier":"Not an id"}]""")) {
            testServer(altinn(body = body)) {
                client.get("/api/genericaccessresources").assertProblem(upstreamError)
            }
        }
    }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/genericaccessresources")

        response.assertProblem(upstreamError)
        assertFalse("Connection refused" in response.bodyAsText())
    }

    private companion object {
        const val GENERIC_ACCESS_RESOURCE = "GenericAccessResource"
    }
}
