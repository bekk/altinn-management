package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateGenericAccessResourceTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun altinn(status: Int = 201, body: String = "") = OutgoingHttpClient { request ->
        sent += request
        response(status, "application/json; charset=utf-8", body)
    }

    private val texts = """{"en":"Example","nb":"Eksempel","nn":"Døme"}"""

    private val minimalBody = """
        {
          "resourceId": "kv-eksempel",
          "title": $texts,
          "description": $texts,
          "delegable": false
        }
    """.trimIndent()

    private val fullBody = """
        {
          "resourceId": "kv-eksempel",
          "title": $texts,
          "description": $texts,
          "delegable": true,
          "delegationDescription": $texts,
          "homepage": "https://kartverket.no/eksempel",
          "contactPoints": [{ "category": "Kundesenter", "email": "kundesenter@kartverket.no" }],
          "keywords": [{ "word": "eksempel", "language": "nb" }],
          "status": "Completed"
        }
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.create(body: String) = client.post("/api/genericaccessresources") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun validationError(vararg errors: FieldError) =
        ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

    private fun malformed(detail: String? = null) = ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)

    private fun assertAltinnNotCalled() = assertTrue(sent.isEmpty(), "Altinn was called: $sent")

    @Test
    fun `creates the resource in Altinn as Kartverket's GenericAccessResource, with an Altinn token`() = testServer(altinn()) {
        create(minimalBody)

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
                  "contactPoints": [],
                  "keywords": [],
                  "delegable": false,
                  "visible": true,
                  "resourceType": "GenericAccessResource",
                  "hasCompetentAuthority": { "organization": "971040238", "orgcode": "kv" },
                  "accessListMode": "Disabled"
                }
                """,
            ),
            Json.parseToJsonElement(request.body!!.decodeToString()),
        )
    }

    @Test
    fun `sends delegable and delegationDescription as given`() = testServer(altinn()) {
        create(fullBody)

        val body = Json.parseToJsonElement(sent.single().body!!.decodeToString()).toString()
        assertTrue(""""delegable":true""" in body, body)
        assertTrue(""""rightDescription":${Json.parseToJsonElement(texts)}""" in body, body)
    }

    @Test
    fun `answers 201 with the resource in this API's model and no Location`() = testServer(altinn()) {
        val response = create(fullBody)

        assertEquals(HttpStatusCode.Created, response.status)
        assertNull(response.headers[HttpHeaders.Location])
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val text = MultilingualText(en = "Example", nb = "Eksempel", nn = "Døme")
        assertEquals(
            GenericAccessResource(
                resourceId = "kv-eksempel",
                title = text,
                description = text,
                delegationDescription = text,
                homepage = "https://kartverket.no/eksempel",
                contactPoints = listOf(ContactPoint(category = "Kundesenter", email = "kundesenter@kartverket.no")),
                keywords = listOf(Keyword(word = "eksempel", language = "nb")),
                status = "Completed",
                delegable = true,
                visible = true,
            ),
            Json.decodeFromString(GenericAccessResource.serializer(), response.bodyAsText()),
        )
        assertFalse("scopes" in response.bodyAsText())
    }

    @Test
    fun `gives 400 with delegationDescription MISSING when delegable is true and it is left out, without asking Altinn`() =
        testServer(altinn()) {
            create(minimalBody.replace(""""delegable": false""", """"delegable": true""")).assertProblem(
                validationError(FieldError("delegationDescription", ValidationCode.MISSING, "delegationDescription is required")),
            )

            assertAltinnNotCalled()
        }

    @Test
    fun `gives 400 listing every missing field at once, without asking Altinn`() = testServer(altinn()) {
        create("{}").assertProblem(
            validationError(
                FieldError("resourceId", ValidationCode.MISSING, "resourceId is required"),
                FieldError("title", ValidationCode.MISSING, "title is required"),
                FieldError("description", ValidationCode.MISSING, "description is required"),
                FieldError("delegable", ValidationCode.MISSING, "delegable is required"),
            ),
        )

        assertAltinnNotCalled()
    }

    @Test
    fun `gives 400 naming every invalid value and missing language, without asking Altinn`() = testServer(altinn()) {
        val body = """
            {
              "resourceId": "Kv eksempel",
              "title": {"en":"Example","nb":"Eksempel"},
              "description": $texts,
              "delegable": true,
              "delegationDescription": {"en":"","nb":"Eksempel","nn":"Døme"},
              "status": "Done"
            }
        """.trimIndent()

        create(body).assertProblem(
            validationError(
                FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be 4 or more of a-z, 0-9, _ and -"),
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                FieldError("delegationDescription.en", ValidationCode.MISSING, "delegationDescription.en is required"),
                FieldError(
                    "status",
                    ValidationCode.INVALID_FORMAT,
                    "status must be one of Completed, Active, UnderDevelopment, Deprecated, Withdrawn",
                ),
            ),
        )

        assertAltinnNotCalled()
    }

    @Test
    fun `gives 400 malformed-request when the body gives resourceId twice, without asking Altinn`() = testServer(altinn()) {
        create(minimalBody.replace(""""resourceId": "kv-eksempel",""", """"resourceId": "kv-eksempel", "resourceId": "kv-annet","""))
            .assertProblem(malformed("The body must be one JSON object, with no name given twice"))

        assertAltinnNotCalled()
    }

    @Test
    fun `gives 400 malformed-request when the body has a field this API sets, without asking Altinn`() = testServer(altinn()) {
        val fields = listOf(
            """"scopes": ["kartverk:eksempel.read"]""",
            """"visible": false""",
            """"accessListMode": "Enabled"""",
            """"resourceType": "MaskinportenSchema"""",
        )
        for (field in fields) {
            create(minimalBody.replaceFirst("{", "{ $field,")).assertProblem(
                malformed("The body must be a JSON object with only this API's fields, each of the right type"),
            )
        }

        assertAltinnNotCalled()
    }

    @Test
    fun `gives 409 when Altinn has a resource with the id`() =
        testServer(altinn(status = 409, body = "\"The Resource already exist: kv-eksempel\"")) {
            create(minimalBody).assertProblem(
                ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, "Altinn already has a resource with this id"),
            )
        }

    @Test
    fun `gives 400 upstream-rejected with Altinn's reasons when Altinn rejects it`() = testServer(
        altinn(
            status = 400,
            body = """
                {
                  "title": "One or more validation errors occurred.",
                  "status": 400,
                  "errors": { "RightDescription": ["Missing RightDescription in english en"] }
                }
            """.trimIndent(),
        ),
    ) {
        create(minimalBody).assertProblem(
            ErrorResponse.of(
                ProblemType.UPSTREAM_REJECTED,
                HttpStatusCode.BadRequest,
                "Altinn rejected the resource: RightDescription: Missing RightDescription in english en",
            ),
        )
    }

    @Test
    fun `gives 502 when Altinn fails`() = testServer(altinn(status = 500, body = "boom")) {
        create(minimalBody).assertProblem(upstreamError)
    }
}
