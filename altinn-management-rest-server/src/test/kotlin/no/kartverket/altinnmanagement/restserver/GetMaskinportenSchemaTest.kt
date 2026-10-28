package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.BokmalText
import no.kartverket.altinnmanagement.client.ContactCategory
import no.kartverket.altinnmanagement.client.EmailAddress
import no.kartverket.altinnmanagement.client.EnglishText
import no.kartverket.altinnmanagement.client.KeywordText
import no.kartverket.altinnmanagement.client.Language
import no.kartverket.altinnmanagement.client.NynorskText
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.ResourceStatus
import no.kartverket.altinnmanagement.client.Scope
import no.kartverket.altinnmanagement.client.WebAddress
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
    fun `answers with Kartverket's MaskinportenSchema in this API's model`() = testServer(altinn()) {
        val response = client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(
            MaskinportenSchema(
                resourceId = ResourceId.of("kv-matrikkel-brukernavn"),
                scopes = listOf(Scope.of("kartverk:matrikkel:brukernavn")),
                title = MultilingualText(
                    en = EnglishText.of("Cadastre API"),
                    nb = BokmalText.of("Matrikkel API"),
                    nn = NynorskText.of("Matrikkel API"),
                ),
                description = MultilingualText(
                    en = EnglishText.of("Machine-to-machine access to the Cadastre."),
                    nb = BokmalText.of("Maskinell tilgang til Matrikkelen."),
                    nn = NynorskText.of("Maskinell tilgang til Matrikkelen."),
                ),
                delegationDescription = MultilingualText(
                    en = EnglishText.of("Allows a system provider to use the Matrikkel API on behalf of your organisation."),
                    nb = BokmalText.of("Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din."),
                    nn = NynorskText.of("Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di."),
                ),
                homepage = WebAddress.of("https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen"),
                contactPoints = listOf(
                    ContactPoint(
                        category = ContactCategory.of("Kartverket Kundesenter"),
                        email = EmailAddress.of("kundesenter@kartverket.no"),
                    ),
                ),
                keywords = listOf(
                    Keyword(KeywordText.of("matrikkel"), Language.NB),
                    Keyword(KeywordText.of("cadastre"), Language.EN),
                ),
                status = ResourceStatus.COMPLETED,
                delegable = true,
                visible = true,
            ),
            response.maskinportenSchema(),
        )
    }

    @Test
    fun `asks Altinn for the resource with the id in the path`() = testServer(altinn()) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        assertEquals(
            "https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn",
            sent.single().url.toString(),
        )
    }

    @Test
    fun `leaves out what the resource does not have, also what Altinn has as empty text`() = testServer(altinn()) {
        val body = Json.parseToJsonElement(client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").bodyAsText()).jsonObject

        assertEquals("""[{"category":"Kartverket Kundesenter","email":"kundesenter@kartverket.no"}]""", body["contactPoints"].toString())
    }

    @Test
    fun `gives the language codes and status in this API's spelling`() = testServer(altinn()) {
        val body = Json.parseToJsonElement(client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").bodyAsText()).jsonObject

        assertEquals(""""COMPLETED"""", body["status"].toString())
        assertEquals("""[{"word":"matrikkel","language":"nb"},{"word":"cadastre","language":"en"}]""", body["keywords"].toString())
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id`() = testServer(altinn(status = 404, body = """{"title":"Not Found","status":404}""")) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(notFound)
    }

    @Test
    fun `gives 404 for another organisation's resource, as if it did not exist`() =
        testServer(altinn(body = resourceJson(organization = "974760223").replace("kundesenter@kartverket.no", "not an email"))) {
            client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(notFound)
        }

    @Test
    fun `gives 404 for Kartverket's resources of other types, as if they did not exist`() = testServer(altinn(body = resourceJson(resourceType = "GenericAccessResource"))) {
        client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(notFound)
    }

    @Test
    fun `gives 404 without asking Altinn for an id that Altinn sends to one of its own endpoints`() = testServer(altinn()) {
        client.get("/api/maskinportenschemas/search").assertProblem(notFound)

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
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
    fun `gives 502 when Kartverket's resource in Altinn has a value that is not valid`() =
        testServer(altinn(body = resourceJson().replace("kartverk:matrikkel:brukernavn", "kartverk:matrikkel brukernavn"))) {
            client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn").assertProblem(upstreamError)
        }

    @Test
    fun `gives 502 when Altinn cannot be reached`() = testServer({ throw IOException("Connection refused") }) {
        val response = client.get("/api/maskinportenschemas/kv-matrikkel-brukernavn")

        response.assertProblem(upstreamError)
        assertFalse("Connection refused" in response.bodyAsText())
    }
}
