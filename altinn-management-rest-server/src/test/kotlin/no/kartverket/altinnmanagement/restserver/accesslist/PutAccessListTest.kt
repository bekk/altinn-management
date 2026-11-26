package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PutAccessListTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 200) = altinnAnswering(sent, status, if (status == 200) altinnList("kv-liste", "Ny") else "")

    private suspend fun ApplicationTestBuilder.put(id: String, body: String) = client.put("/api/accesslists/$id") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `changes the list in Altinn, only if Kartverket has it, and answers with it`() = testServer(altinn()) {
        val response = put("kv-liste", """{"accessListId":"kv-liste","name":"Ny"}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(accessList("kv-liste", "Ny"), response.accessList())
        val request = sent.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals("$ALTINN_LISTS/kv-liste", request.url.toString())
        assertEquals("*", request.headers["If-Match"])
        assertEquals("""{"name":"Ny","description":""}""", request.body!!.decodeToString())
    }

    @Test
    fun `gives 404 when Kartverket has no list with the id, which Altinn does not create`() = testServer(altinn(status = 412)) {
        put("kv-finnes-ikke", """{"name":"Ny"}""").assertProblem(accessListNotFound)

        assertEquals("*", sent.single().headers["If-Match"])
    }

    @Test
    fun `gives 400 for an id with a colon or capital letters, without asking Altinn`() = testServer(altinn()) {
        for (id in listOf("kv:liste", "KV-LISTE")) put(id, """{"name":"Ny"}""").assertProblem(invalidId("id"))

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 when the name is missing or the body has another id, without asking Altinn`() = testServer(altinn()) {
        put("kv-liste", """{"accessListId":"kv-annen"}""").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(
                    FieldError("accessListId", ValidationCode.INVALID_FORMAT, "accessListId must be the id in the path"),
                    FieldError("name", ValidationCode.MISSING, "name is required"),
                ),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
