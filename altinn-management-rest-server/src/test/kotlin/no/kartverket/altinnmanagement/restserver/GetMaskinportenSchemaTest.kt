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
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetMaskinportenSchemaTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 200, body: String = resourceJson()) = OutgoingHttpClient { request ->
        sent += request
        response(status, "application/json; charset=utf-8", body)
    }

    private val notFound = ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no MaskinportenSchema with this id")

    private val upstreamError = ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed")

    private suspend fun HttpResponse.maskinportenSchema(): MaskinportenSchema =
        Json.decodeFromString(MaskinportenSchema.serializer(), bodyAsText())

    private suspend fun HttpResponse.assertProblem(expected: ErrorResponse) {
        assertEquals(expected.status, status.value)
        assertEquals(ContentType.Application.ProblemJson, contentType()?.withoutParameters())
        assertEquals(expected, errorResponse())
    }

    @Test
    fun `answers with the MaskinportenSchema in this API's model`() = testServer(altinn()) {
        val response = client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(
            MaskinportenSchema(
                resourceId = "kv-matrikkel-brukernavn",
                scopes = listOf("kartverk:matrikkel:brukernavn"),
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
            response.maskinportenSchema(),
        )
    }

    @Test
    fun `asks Altinn for the resource with the id in the path, with a GET and without a token`() = testServer(altinn()) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn", request.url.toString())
        assertEquals(mapOf("Accept" to "application/json"), request.headers)
    }

    @Test
    fun `passes on Altinn's values as they are, also those writes through this API would not accept`() =
        testServer(altinn(body = resourceJson().replace("\"contactPage\": \"\"", "\"contactPage\": \"skip.kartverket.no\""))) {
            val body = Json.parseToJsonElement(client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").bodyAsText()).jsonObject

            assertEquals(
                """[{"category":"Kartverket Kundesenter","email":"kundesenter@kartverket.no","telephone":"","contactPage":"skip.kartverket.no"}]""",
                body["contactPoints"].toString(),
            )
        }

    @Test
    fun `leaves out the fields the resource does not have`() =
        testServer(altinn(body = """{"identifier":"kv-matrikkel-brukernavn","title":{"nb":"Matrikkel API"},"resourceType":"MaskinportenSchema"}""")) {
            val body = Json.parseToJsonElement(client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").bodyAsText()).jsonObject

            assertEquals(
                setOf("resourceId", "title"),
                body.keys,
            )
            assertEquals("""{"nb":"Matrikkel API"}""", body["title"].toString())
        }

    @Test
    fun `gives 404 when Altinn has no resource with the id`() = testServer(altinn(status = 404, body = """{"title":"Not Found","status":404}""")) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(notFound)
    }

    @Test
    fun `gives 404 for resources of other types, as if they did not exist`() = testServer(altinn(body = resourceJson(resourceType = "GenericAccessResource"))) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(notFound)
    }

    @Test
    fun `gives 400 for an id Altinn would not accept, without asking Altinn`() = testServer(altinn()) {
        for (id in listOf("abc", "Kv-matrikkel", "kv.matrikkel", "..", "..%2Fsearch", "kv%2Fx")) {
            client.get("/api/maskinportenschemas/$id").assertProblem(
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
    fun `gives 502 when Altinn answers with an error`() = testServer(altinn(status = 500, body = "boom")) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn answers with something that is not a resource`() = testServer(altinn(body = "[]")) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(upstreamError)
    }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        response.assertProblem(upstreamError)
        assertFalse("Connection refused" in response.bodyAsText())
    }
}
