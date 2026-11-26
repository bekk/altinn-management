package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateAccessListTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 200) =
        altinnAnswering(sent, status, if (status == 200) altinnList("kv-ny", "Ny", "Om lista") else "")

    private suspend fun ApplicationTestBuilder.create(body: String) = client.post("/api/accesslists") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `creates the list in Altinn, only if Kartverket has none with the id, and answers 201 with it and where to get it`() =
        testServer(altinn()) {
            val response = create("""{"accessListId":"kv-ny","name":"Ny","description":"Om lista"}""")

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("/api/accesslists/kv-ny", response.headers[HttpHeaders.Location])
            assertEquals(accessList("kv-ny", "Ny", "Om lista"), response.accessList())
            val request = sent.single()
            assertEquals(HttpMethod.PUT, request.method)
            assertEquals("$ALTINN_LISTS/kv-ny", request.url.toString())
            assertEquals("*", request.headers["If-None-Match"])
            assertEquals("Bearer $TEST_TOKEN", request.headers["Authorization"])
            assertEquals("""{"name":"Ny","description":"Om lista"}""", request.body!!.decodeToString())
        }

    @Test
    fun `gives 409 when Kartverket has a list with the id`() = testServer(altinn(status = 412)) {
        create("""{"accessListId":"kv-ny","name":"Ny"}""").assertProblem(
            ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, "Altinn already has an access list with this id"),
        )
    }

    @Test
    fun `gives 400 for an id with a colon or capital letters, without asking Altinn`() = testServer(altinn()) {
        for (id in listOf("kv:ny", "KV-NY")) {
            create("""{"accessListId":"$id","name":"Ny"}""").assertProblem(invalidId("accessListId"))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 listing every missing field at once, without asking Altinn`() = testServer(altinn()) {
        create("""{"name":" "}""").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(
                    FieldError("accessListId", ValidationCode.MISSING, "accessListId is required"),
                    FieldError("name", ValidationCode.MISSING, "name is required"),
                ),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
