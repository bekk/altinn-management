package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAccessListTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 200) = OutgoingHttpClient { request ->
        sent += request
        if (status == 200) {
            response(
                200,
                "application/json",
                """{"urn":"urn:altinn:access-list:kv:kv-matrikkel-kunder","identifier":"kv-matrikkel-kunder","name":"Kunder","description":"","createdAt":"2026-11-20T10:15:30.123456+00:00","updatedAt":"2026-11-21T08:00:00+00:00"}""",
            )
        } else {
            response(status, "application/json", """{"title":"Not Found","status":404}""")
        }
    }

    @Test
    fun `gets a list by its id in this API's model, asking Altinn with a token`() = testServer(altinn()) {
        val response = client.get("/api/accesslists/kv-matrikkel-kunder")

        val expected = AccessList(
            accessListId = "kv-matrikkel-kunder",
            urn = "urn:altinn:access-list:kv:kv-matrikkel-kunder",
            name = "Kunder",
            description = "",
            createdAt = "2026-11-20T10:15:30.123456+00:00",
            updatedAt = "2026-11-21T08:00:00+00:00",
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, Json.decodeFromString(AccessList.serializer(), response.bodyAsText()))
        val request = sent.single()
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/access-lists/kv/kv-matrikkel-kunder", request.url.toString())
        assertEquals("Bearer $TEST_TOKEN", request.headers["Authorization"])
    }

    @Test
    fun `gives 404 when Altinn has no list with the id`() = testServer(altinn(status = 404)) {
        client.get("/api/accesslists/kv-finnes-ikke").assertProblem(
            ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no access list with this id"),
        )
    }
}
