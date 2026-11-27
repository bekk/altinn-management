package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
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

class AccessListMembersTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val noMembers = """{"links":{},"data":[]}"""

    private suspend fun ApplicationTestBuilder.add(body: String, id: String = "kv-liste") = client.post("/api/accesslists/$id/members") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun invalid(vararg fields: String) = ErrorResponse.of(
        ProblemType.VALIDATION_ERROR,
        HttpStatusCode.BadRequest,
        errors = fields.map { FieldError(it, ValidationCode.INVALID_FORMAT, "$it must be 9 digits") },
    )

    @Test
    fun `lists the members, leaving out the organisation number of a member that is not an organisation`() {
        val members = """{"links":{},"data":[
            {"id":"urn:altinn:party:uuid:a1","since":"2026-11-20T10:15:30+00:00","identifiers":{"urn:altinn:party:uuid":"a1","urn:altinn:party:id":1,"urn:altinn:organization:identifier-no":"991825827"}},
            {"id":"urn:altinn:party:uuid:b2","since":"2026-11-21T08:00:00+00:00","identifiers":{"urn:altinn:party:uuid":"b2","urn:altinn:party:id":2}}
        ]}"""
        testServer(altinnAnswering(sent, 200, members)) {
            val response = client.get("/api/accesslists/kv-liste/members")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """[{"organisationNumber":"991825827","addedAt":"2026-11-20T10:15:30+00:00"},{"addedAt":"2026-11-21T08:00:00+00:00"}]""",
                response.bodyAsText(),
            )
            assertEquals(
                listOf(AccessListMember("991825827", "2026-11-20T10:15:30+00:00"), AccessListMember(null, "2026-11-21T08:00:00+00:00")),
                Json.decodeFromString(ListSerializer(AccessListMember.serializer()), response.bodyAsText()),
            )
            assertEquals("$ALTINN_LISTS/kv-liste/members", sent.single().url.toString())
        }
    }

    @Test
    fun `gives 404 for the members of a list Kartverket does not have`() = testServer(altinnAnswering(sent, 404)) {
        client.get("/api/accesslists/kv-finnes-ikke/members").assertProblem(accessListNotFound)
    }

    @Test
    fun `adds the organisations in Altinn and answers 204`() = testServer(altinnAnswering(sent, 200, noMembers)) {
        val response = add("""{"organisationNumbers":["991825827","971040238"]}""")

        assertEquals(HttpStatusCode.NoContent, response.status)
        val request = sent.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("$ALTINN_LISTS/kv-liste/members", request.url.toString())
        assertEquals(
            """{"data":["urn:altinn:organization:identifier-no:991825827","urn:altinn:organization:identifier-no:971040238"]}""",
            request.body!!.decodeToString(),
        )
    }

    @Test
    fun `gives 404 when adding to a list Kartverket does not have`() = testServer(altinnAnswering(sent, 404)) {
        add("""{"organisationNumbers":["991825827"]}""", id = "kv-finnes-ikke").assertProblem(accessListNotFound)
    }

    @Test
    fun `gives 400 for every organisation number that is not 9 digits, without asking Altinn`() = testServer(altinnAnswering(sent, 200)) {
        add("""{"organisationNumbers":["991825827","99182582","9918258270","99182582a","٩٩١٨٢٥٨٢٧"]}""").assertProblem(
            invalid("organisationNumbers[1]", "organisationNumbers[2]", "organisationNumbers[3]", "organisationNumbers[4]"),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for more than 100 organisations or none sent, without asking Altinn`() = testServer(altinnAnswering(sent, 200)) {
        val numbers = (0..100).joinToString(",") { "\"${991825827 + it}\"" }
        add("""{"organisationNumbers":[$numbers]}""").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(
                    FieldError("organisationNumbers", ValidationCode.INVALID_FORMAT, "organisationNumbers must have at most 100 entries"),
                ),
            ),
        )
        add("{}").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(FieldError("organisationNumbers", ValidationCode.MISSING, "organisationNumbers is required")),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 when Altinn does not know an organisation`() {
        val unknown = """{"type":"urn:altinn:error:RR-00001","title":"One or more party references not found.","status":400,"code":"RR-00001"}"""
        testServer(altinnAnswering(sent, 400, unknown)) {
            add("""{"organisationNumbers":["991825827"]}""").assertProblem(
                ErrorResponse.of(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.BadRequest, "Altinn does not know one or more of the organisations"),
            )
        }
    }

    @Test
    fun `removes the organisation in Altinn and answers 204`() = testServer(altinnAnswering(sent, 200, noMembers)) {
        val response = client.delete("/api/accesslists/kv-liste/members/991825827")

        assertEquals(HttpStatusCode.NoContent, response.status)
        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("$ALTINN_LISTS/kv-liste/members", request.url.toString())
        assertEquals("""{"data":["urn:altinn:organization:identifier-no:991825827"]}""", request.body!!.decodeToString())
    }

    @Test
    fun `gives 404 when removing from a list Kartverket does not have`() = testServer(altinnAnswering(sent, 404)) {
        client.delete("/api/accesslists/kv-finnes-ikke/members/991825827").assertProblem(accessListNotFound)
    }

    @Test
    fun `gives 400 for a list id or organisation number that is not valid, without asking Altinn`() = testServer(altinnAnswering(sent, 200)) {
        client.delete("/api/accesslists/KV-LISTE/members/991825827").assertProblem(invalidId("id"))
        client.delete("/api/accesslists/kv-liste/members/99182582").assertProblem(invalid("organisationNumber"))
        add("""{"organisationNumbers":["991825827"]}""", id = "KV-LISTE").assertProblem(invalidId("id"))

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
