package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.altinnmanagement.restserver.filtering.ExternalFilteringClient
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListAccessListsTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val certificate = "Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api"

    private fun list(id: String) = """
        {"urn":"urn:altinn:access-list:kv:$id","identifier":"$id","name":"Kunder","description":"","createdAt":"2026-11-20T10:15:30.123456+00:00","updatedAt":"2026-11-21T08:00:00+00:00"}
    """.trimIndent()

    /** Altinn with one page of lists with the ids [ids]. */
    private fun altinn(vararg ids: String) = OutgoingHttpClient { request ->
        sent += request
        response(200, "application/json", ids.joinToString(prefix = """{"links":{},"data":[""", postfix = "]}") { list(it) })
    }

    private fun filteringAllowing(resources: String) = ExternalFiltering(
        ExternalFilteringClient(
            URI("https://filtering.test/altinn"),
            OutgoingHttpClient { response(200, "application/json", """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":$resources}}""") },
        ),
    )

    private suspend fun HttpResponse.accessLists(): List<AccessList> =
        Json.decodeFromString(ListSerializer(AccessList.serializer()), bodyAsText())

    @Test
    fun `lists the access lists in this API's model, asking Altinn with a token`() = testServer(altinn("kv-matrikkel-kunder")) {
        val response = client.get("/api/accesslists")

        val expected = AccessList(
            accessListId = "kv-matrikkel-kunder",
            urn = "urn:altinn:access-list:kv:kv-matrikkel-kunder",
            name = "Kunder",
            description = "",
            createdAt = "2026-11-20T10:15:30.123456+00:00",
            updatedAt = "2026-11-21T08:00:00+00:00",
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(expected), response.accessLists())
        assertEquals("Bearer $TEST_TOKEN", sent.single().headers["Authorization"])
    }

    @Test
    fun `lists only the lists under one of the calling app's prefixes for resources, not its exact ids`() = testServer(
        altinn = altinn("kv-matrikkel-kunder", "nrl-kunder", "res-exact"),
        filtering = filteringAllowing("""{"exact":["res-exact"],"prefix":["nrl-"]}"""),
    ) {
        val response = client.get("/api/accesslists") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(listOf("nrl-kunder"), response.accessLists().map { it.accessListId })
    }

    @Test
    fun `gives 400 when filtering is on and X-Forwarded-Client-Cert is missing, without asking Altinn`() =
        testServer(altinn("nrl-kunder"), filteringAllowing("""{"exact":[],"prefix":["nrl-"]}""")) {
            client.get("/api/accesslists").assertProblem(
                ErrorResponse.of(
                    ProblemType.VALIDATION_ERROR,
                    HttpStatusCode.BadRequest,
                    errors = listOf(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
                ),
            )

            assertTrue(sent.isEmpty(), "Altinn was called: $sent")
        }
}
