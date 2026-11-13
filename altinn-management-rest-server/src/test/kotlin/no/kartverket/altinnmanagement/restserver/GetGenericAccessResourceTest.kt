package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetGenericAccessResourceTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val genericAccessResourceJson = resourceJson(resourceType = "GenericAccessResource")

    private fun altinn(status: Int = 200, body: String = genericAccessResourceJson) = OutgoingHttpClient { request ->
        sent += request
        response(status, "application/json; charset=utf-8", body)
    }

    private suspend fun HttpResponse.genericAccessResource(): GenericAccessResource =
        Json.decodeFromString(GenericAccessResource.serializer(), bodyAsText())

    @Test
    fun `answers with the GenericAccessResource in this API's model`() = testServer(altinn()) {
        val response = client.get("/api/genericaccessresources/kv-matrikkel-brukernavn")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(
            GenericAccessResource(
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
            ),
            response.genericAccessResource(),
        )
    }

    @Test
    fun `asks Altinn for the resource with the id in the path, with a GET and without a token`() = testServer(altinn()) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn")

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn", request.url.toString())
        assertEquals(mapOf("Accept" to "application/json"), request.headers)
    }

    @Test
    fun `has no scopes, also when the resource in Altinn has Maskinporten scopes`() = testServer(altinn()) {
        val response = client.get("/api/genericaccessresources/kv-matrikkel-brukernavn")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse("scopes" in body.keys, body.toString())
    }

    @Test
    fun `answers a withdrawn resource with its status`() = testServer(
        altinn(body = genericAccessResourceJson.replace("\"status\": \"Completed\"", "\"status\": \"Withdrawn\"").replace("\"visible\": true", "\"visible\": false")),
    ) {
        val response = client.get("/api/genericaccessresources/kv-matrikkel-brukernavn")

        assertEquals(HttpStatusCode.OK, response.status)
        val resource = response.genericAccessResource()
        assertEquals("Withdrawn", resource.status)
        assertEquals(false, resource.visible)
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id`() = testServer(altinn(status = 404, body = """{"title":"Not Found","status":404}""")) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").assertProblem(genericAccessResourceNotFound)
    }

    @Test
    fun `gives 404 for resources of other types, as if they did not exist`() = testServer(altinn(body = resourceJson())) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").assertProblem(genericAccessResourceNotFound)
    }

    @Test
    fun `gives 404 for resources another organisation owns`() =
        testServer(altinn(body = genericAccessResourceJson.replace("\"971040238\"", "\"981544315\""))) {
            client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").assertProblem(genericAccessResourceNotFound)
        }

    @Test
    fun `gives 404 for a resource with no owner`() =
        testServer(altinn(body = """{"identifier":"kv-matrikkel-brukernavn","resourceType":"GenericAccessResource"}""")) {
            client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").assertProblem(genericAccessResourceNotFound)
        }

    @Test
    fun `gives 404 for an id that is one of Altinn's fixed routes, without asking Altinn`() = testServer(altinn(body = "[]")) {
        for (id in listOf("resourcelist", "export", "search", "updated", "changes", "orgs")) {
            client.get("/api/genericaccessresources/$id").assertProblem(genericAccessResourceNotFound)
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for an id Altinn would not accept, without asking Altinn`() = testServer(altinn()) {
        for (id in listOf("abc", "Kv-matrikkel", "kv.matrikkel", "..", "..%2Fsearch", "kv%2Fx")) {
            client.get("/api/genericaccessresources/$id").assertProblem(
                ErrorResponse.of(
                    ProblemType.VALIDATION_ERROR,
                    HttpStatusCode.BadRequest,
                    errors = listOf(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
                ),
            )
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `neither asks the filtering service nor needs a client certificate`() = testServer(
        altinn(),
        filtering = ExternalFiltering(
            ExternalFilteringClient(
                URI("https://filtering.test/altinn"),
                OutgoingHttpClient { error("the filtering service was called: ${it.url}") },
            ),
        ),
    ) {
        assertEquals(HttpStatusCode.OK, client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").status)
    }

    @Test
    fun `gives 502 when Altinn answers with an error`() = testServer(altinn(status = 500, body = "boom")) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn").assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/genericaccessresources/kv-matrikkel-brukernavn")

        response.assertProblem(upstreamError)
        assertFalse("Connection refused" in response.bodyAsText())
    }
}
