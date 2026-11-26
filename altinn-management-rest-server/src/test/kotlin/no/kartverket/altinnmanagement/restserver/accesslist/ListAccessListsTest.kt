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

    private fun list(id: String, connections: String = "") = """
        {"urn":"urn:altinn:access-list:kv:$id","identifier":"$id","name":"Kunder","description":"","createdAt":"2026-11-20T10:15:30.123456+00:00","updatedAt":"2026-11-21T08:00:00+00:00"$connections}
    """.trimIndent()

    /** Altinn with one page of lists with the ids [ids]. */
    private fun altinn(vararg ids: String) = altinnWith(ids.map { list(it) })

    /** Altinn with one page of lists, each connected to `kv-geonorge` or not, as Altinn gives them with that resource. */
    private fun altinnConnecting(vararg connected: Pair<String, Boolean>) = altinnWith(
        connected.map { (id, isConnected) ->
            val connection = """{"resourceIdentifier":"kv-geonorge","createdAt":"2026-11-20T10:15:30+00:00","updatedAt":"2026-11-20T10:15:30+00:00"}"""
            list(id, ""","resourceConnections":[${if (isConnected) connection else ""}]""")
        },
    )

    private fun altinnWith(lists: List<String>) = OutgoingHttpClient { request ->
        sent += request
        response(200, "application/json", lists.joinToString(prefix = """{"links":{},"data":[""", postfix = "]}"))
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
    fun `lists only the lists connected to the resource, asking Altinn for its connections`() =
        testServer(altinnConnecting("kv-a" to false, "kv-b" to true)) {
            val response = client.get("/api/accesslists?resource=kv-geonorge")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("kv-b"), response.accessLists().map { it.accessListId })
            assertEquals("include=resources&resource=kv-geonorge", sent.single().url.query)
        }

    @Test
    fun `lists only the connected lists under one of the calling app's prefixes for resources`() = testServer(
        altinn = altinnConnecting("kv-b" to true, "nrl-a" to true, "nrl-c" to false),
        filtering = filteringAllowing("""{"exact":["kv-b"],"prefix":["nrl-"]}"""),
    ) {
        val response = client.get("/api/accesslists?resource=kv-geonorge") { header(CLIENT_CERTIFICATE_HEADER, certificate) }

        assertEquals(listOf("nrl-a"), response.accessLists().map { it.accessListId })
    }

    @Test
    fun `gives 400 for a resource that is not a resource's id or is sent twice, without asking Altinn`() =
        testServer(altinnConnecting("kv-b" to true)) {
            val invalid = FieldError("resource", ValidationCode.INVALID_FORMAT, "resource must be 4 or more of a-z, 0-9, _ and -")
            val twice = FieldError("resource", ValidationCode.INVALID_FORMAT, "resource must be sent once")
            val cases = mapOf("resource=KV-GEONORGE" to invalid, "resource=" to invalid, "resource=kv-a1&resource=kv-a2" to twice)
            for ((query, error) in cases) {
                client.get("/api/accesslists?$query").assertProblem(
                    ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = listOf(error)),
                )
            }

            assertTrue(sent.isEmpty(), "Altinn was called: $sent")
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
