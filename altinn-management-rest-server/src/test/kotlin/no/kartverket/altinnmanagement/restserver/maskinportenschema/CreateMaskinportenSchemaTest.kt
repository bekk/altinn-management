package no.kartverket.altinnmanagement.restserver.maskinportenschema

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.ContactPoint
import no.kartverket.altinnmanagement.restserver.Keyword
import no.kartverket.altinnmanagement.restserver.MultilingualText
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.errorResponse
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateMaskinportenSchemaTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 201, body: String = "") = OutgoingHttpClient { request ->
        sent += request
        response(status, "application/json; charset=utf-8", body)
    }

    private val texts = """{"en":"Example","nb":"Eksempel","nn":"Døme"}"""

    private val fullBody = """
        {
          "resourceId": "kv-eksempel",
          "scopes": ["kartverk:eksempel.read", "kartverk:eksempel.write"],
          "title": $texts,
          "description": $texts,
          "delegationDescription": $texts,
          "homepage": "https://kartverket.no/eksempel",
          "contactPoints": [{ "category": "Kundesenter", "email": "kundesenter@kartverket.no" }],
          "keywords": [{ "word": "eksempel", "language": "nb" }],
          "status": "Completed"
        }
    """.trimIndent()

    private val minimalBody = """
        {
          "resourceId": "kv-eksempel",
          "scopes": ["kartverk:eksempel.read"],
          "title": $texts,
          "description": $texts,
          "delegationDescription": $texts
        }
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.create(body: String, contentType: ContentType = ContentType.Application.Json) =
        client.post("/api/maskinportenschemas") {
            contentType(contentType)
            setBody(body)
        }

    private suspend fun HttpResponse.assertProblem(expected: ErrorResponse) {
        assertEquals(expected.status, status.value)
        assertEquals(ContentType.Application.ProblemJson, contentType()?.withoutParameters())
        assertEquals(expected, errorResponse())
    }

    private fun validationError(vararg errors: FieldError) =
        ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

    @Test
    fun `answers 201 with where to get the resource and the resource as a GET would answer it`() = testServer(altinn()) {
        val response = create(fullBody)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("/api/maskinportenschemas/kv-eksempel", response.headers[HttpHeaders.Location])
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val text = MultilingualText(en = "Example", nb = "Eksempel", nn = "Døme")
        assertEquals(
            MaskinportenSchema(
                resourceId = "kv-eksempel",
                scopes = listOf("kartverk:eksempel.read", "kartverk:eksempel.write"),
                title = text,
                description = text,
                delegationDescription = text,
                homepage = "https://kartverket.no/eksempel",
                contactPoints = listOf(ContactPoint(category = "Kundesenter", email = "kundesenter@kartverket.no")),
                keywords = listOf(Keyword(word = "eksempel", language = "nb")),
                status = "Completed",
                delegable = true,
                visible = true,
                accessListMode = "Disabled",
            ),
            Json.decodeFromString(MaskinportenSchema.serializer(), response.bodyAsText()),
        )
    }

    @Test
    fun `sends Altinn the resource owned by Kartverket, with a POST and an Altinn token`() = testServer(altinn()) {
        create(fullBody)

        val request = sent.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource", request.url.toString())
        assertEquals(
            mapOf("Authorization" to "Bearer $TEST_TOKEN", "Content-Type" to "application/json", "Accept" to "application/json"),
            request.headers,
        )
        assertEquals(
            Json.parseToJsonElement(
                """
                {
                  "identifier": "kv-eksempel",
                  "title": $texts,
                  "description": $texts,
                  "rightDescription": $texts,
                  "homepage": "https://kartverket.no/eksempel",
                  "status": "Completed",
                  "contactPoints": [{ "category": "Kundesenter", "email": "kundesenter@kartverket.no" }],
                  "keywords": [{ "word": "eksempel", "language": "nb" }],
                  "resourceReferences": [
                    { "referenceSource": "Default", "reference": "kartverk:eksempel.read", "referenceType": "MaskinportenScope" },
                    { "referenceSource": "Default", "reference": "kartverk:eksempel.write", "referenceType": "MaskinportenScope" }
                  ],
                  "delegable": true,
                  "visible": true,
                  "resourceType": "MaskinportenSchema",
                  "hasCompetentAuthority": { "organization": "971040238", "orgcode": "kv" },
                  "accessListMode": "Disabled"
                }
                """,
            ),
            Json.parseToJsonElement(request.body!!.decodeToString()),
        )
    }

    @Test
    fun `sends empty lists of contact points and keywords, and no homepage or status, when the caller leaves them out`() = testServer(altinn()) {
        assertEquals(HttpStatusCode.Created, create(minimalBody).status)

        val body = Json.parseToJsonElement(sent.single().body!!.decodeToString()).toString()
        assertTrue(""""contactPoints":[]""" in body, body)
        assertTrue(""""keywords":[]""" in body, body)
        assertFalse("homepage" in body, body)
        assertFalse("status" in body, body)
    }

    @Test
    fun `gives 400 naming every missing field, without asking Altinn`() = testServer(altinn()) {
        create("{}").assertProblem(
            validationError(
                FieldError("resourceId", ValidationCode.MISSING, "resourceId is required"),
                FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                FieldError("title", ValidationCode.MISSING, "title is required"),
                FieldError("description", ValidationCode.MISSING, "description is required"),
                FieldError("delegationDescription", ValidationCode.MISSING, "delegationDescription is required"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 naming every invalid value and missing language, without asking Altinn`() = testServer(altinn()) {
        val body = """
            {
              "resourceId": "Kv eksempel",
              "scopes": ["kartverk:eksempel.read", " "],
              "title": {"en":"Example","nb":"Eksempel"},
              "description": {"en":"","nb":"Eksempel","nn":"Døme"},
              "delegationDescription": $texts,
              "status": "Done"
            }
        """.trimIndent()

        create(body).assertProblem(
            validationError(
                FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be 4 or more of a-z, 0-9, _ and -"),
                FieldError("scopes[1]", ValidationCode.INVALID_FORMAT, "scopes[1] must not be blank"),
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                FieldError("description.en", ValidationCode.MISSING, "description.en is required"),
                FieldError(
                    "status",
                    ValidationCode.INVALID_FORMAT,
                    "status must be one of Completed, Active, UnderDevelopment, Deprecated, Withdrawn",
                ),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a resourceId that Altinn uses as a route, without asking Altinn`() = testServer(altinn()) {
        for (id in listOf("resourcelist", "export", "search", "updated", "changes", "orgs")) {
            create(minimalBody.replace("kv-eksempel", id)).assertProblem(
                validationError(
                    FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must not be $id, which Altinn uses as a route"),
                ),
            )
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 when the list of scopes is empty`() = testServer(altinn()) {
        create(minimalBody.replace("""["kartverk:eksempel.read"]""", "[]")).assertProblem(
            validationError(FieldError("scopes", ValidationCode.MISSING, "scopes is required")),
        )
    }

    @Test
    fun `gives 400 for a scope with whitespace, which no OAuth scope can hold, without asking Altinn`() = testServer(altinn()) {
        create(minimalBody.replace("""["kartverk:eksempel.read"]""", """["kartverk:ok", "kartverk:a b", "kartverk:a\tb"]""")).assertProblem(
            validationError(
                FieldError("scopes[1]", ValidationCode.INVALID_FORMAT, "scopes[1] must not contain whitespace"),
                FieldError("scopes[2]", ValidationCode.INVALID_FORMAT, "scopes[2] must not contain whitespace"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 malformed-request for a body this API cannot read, without asking Altinn`() = testServer(altinn()) {
        val unreadable = listOf(
            "not json",
            "",
            "[]",
            minimalBody.replace("""["kartverk:eksempel.read"]""", "\"kartverk:eksempel.read\""),
            minimalBody.replace("delegationDescription", "rightDescription"),
        )
        for (body in unreadable) {
            val response = create(body)

            assertEquals(HttpStatusCode.BadRequest, response.status, "for $body")
            assertEquals(ProblemType.MALFORMED_REQUEST, response.errorResponse().type, "for $body")
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    private suspend fun ApplicationTestBuilder.createBytes(body: ByteArray, contentType: String?) = client.post("/api/maskinportenschemas") {
        contentType?.let { header(HttpHeaders.ContentType, it) }
        setBody(body)
    }

    private fun malformed(detail: String) = ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)

    @Test
    fun `gives 400 for a body that gives a name twice or holds more than one value, so it is read as filtering reads it`() = testServer(altinn()) {
        val ambiguous = listOf(
            minimalBody.replaceFirst("{", """{ "scopes": ["kartverk:annet"],"""),
            minimalBody.replace(""""resourceId": "kv-eksempel",""", """"resourceId": "kv-eksempel", "resourceId": "kv-annet","""),
            "$minimalBody {}",
        )
        for (ambiguousBody in ambiguous) {
            create(ambiguousBody).assertProblem(malformed("The body must be one JSON object, with no name given twice"))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body that is not UTF-8, whatever charset it names`() = testServer(altinn()) {
        createBytes(minimalBody.toByteArray(Charsets.ISO_8859_1), "application/json; charset=ISO-8859-1")
            .assertProblem(malformed("The body must be UTF-8"))

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body that is not sent as JSON`() = testServer(altinn()) {
        for (contentType in listOf(null, "text/plain", "application/xml", "application/json-patch+json")) {
            createBytes(minimalBody.toByteArray(), contentType)
                .assertProblem(malformed("The body must be JSON, sent with Content-Type application/json"))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 409 when Altinn already has a resource with the id`() =
        testServer(altinn(status = 409, body = "\"The Resource already exist: kv-eksempel\"")) {
            create(minimalBody).assertProblem(
                ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, "Altinn already has a resource with this id"),
            )
        }

    @Test
    fun `gives 400 with Altinn's reasons when Altinn rejects the resource`() = testServer(
        altinn(
            status = 400,
            body = """
                {
                  "type": "https://tools.ietf.org/html/rfc9110#section-15.5.1",
                  "title": "One or more validation errors occurred.",
                  "status": 400,
                  "errors": { "Title": ["Missing title in english en"], "Description": ["Missing Description in bokmal nb"] },
                  "traceId": "00-abc-def-00"
                }
            """.trimIndent(),
        ),
    ) {
        create(minimalBody).assertProblem(
            ErrorResponse.of(
                ProblemType.UPSTREAM_REJECTED,
                HttpStatusCode.BadRequest,
                "Altinn rejected the resource: Title: Missing title in english en; Description: Missing Description in bokmal nb",
            ),
        )
    }
}
