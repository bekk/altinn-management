package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PutGenericAccessResourcePolicyTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val genericAccessResource = resourceJson("GenericAccessResource")

    private fun altinn(resource: String? = genericAccessResource, status: Int = 201, body: String = "") = OutgoingHttpClient { request ->
        sent += request
        when {
            request.method != HttpMethod.GET -> response(status, "application/json; charset=utf-8", body)
            resource == null -> response(404, "application/problem+json", """{"status":404}""")
            else -> response(200, "application/json; charset=utf-8", resource)
        }
    }

    private val geonorge = """
        {
          "minimumAuthenticationLevel": { "endUser": 4, "organisation": 3 },
          "rules": [
            { "subjects": ["urn:altinn:accesspackage:eksplisitt"], "actions": ["access"] },
            {
              "description": "Datasets",
              "subjects": ["urn:altinn:accesspackage:eksplisitt"],
              "actions": ["download"],
              "subResources": [{ "urn:kv:geonorge": "dataset" }]
            }
          ]
        }
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.putPolicy(body: String = geonorge, id: String = "kv-matrikkel-brukernavn") =
        client.put("/api/genericaccessresources/$id/policy") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private val notFound = ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no GenericAccessResource with this id")

    private fun validationError(vararg errors: FieldError) =
        ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

    private fun assertNothingWritten() = assertTrue(sent.all { it.method == HttpMethod.GET }, "Altinn got a write: $sent")

    @Test
    fun `answers 204 with no body`() = testServer(altinn()) {
        val response = putPolicy()

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `puts the policy to Altinn as the form file policyFile, with an Altinn token`() = testServer(altinn()) {
        putPolicy()

        val request = sent.single { it.method != HttpMethod.GET }
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn/policy", request.url.toString())
        assertEquals(
            mapOf(
                "Authorization" to "Bearer $TEST_TOKEN",
                "Content-Type" to "multipart/form-data; boundary=AltinnManagementPolicyBoundary",
            ),
            request.headers,
        )
        assertTrue("name=\"policyFile\"" in request.body!!.decodeToString())
    }

    @Test
    fun `sends the rules, the sub-resources and the levels from the body`() = testServer(altinn()) {
        putPolicy()

        val policy = sent.single { it.method != HttpMethod.GET }.body!!.decodeToString()
        val value = """<xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">"""
        assertTrue("${value}eksplisitt</xacml:AttributeValue>" in policy, policy)
        assertTrue("${value}download</xacml:AttributeValue>" in policy, policy)
        assertTrue("${value}dataset</xacml:AttributeValue>" in policy, policy)
        assertTrue("""AttributeId="urn:kv:geonorge"""" in policy, policy)
        assertTrue("<xacml:Description>Datasets</xacml:Description>" in policy, policy)
        assertTrue("""Category="urn:altinn:minimum-authenticationlevel-org">""" in policy, policy)
    }

    @Test
    fun `gives 400 with every invalid field at once, without asking Altinn`() = testServer(altinn()) {
        val body = """
            {
              "rules": [
                { "subjects": ["eksplisitt", "urn:altinn:rolecode:dagl"], "actions": [] },
                { "actions": ["read", " "] }
              ]
            }
        """.trimIndent()

        putPolicy(body).assertProblem(
            validationError(
                FieldError(
                    "rules[0].subjects[0]",
                    ValidationCode.INVALID_FORMAT,
                    "rules[0].subjects[0] must be a URN, like urn:altinn:accesspackage:eksplisitt",
                ),
                FieldError("rules[0].actions", ValidationCode.MISSING, "rules[0].actions is required"),
                FieldError("rules[1].subjects", ValidationCode.MISSING, "rules[1].subjects is required"),
                FieldError("rules[1].actions[1]", ValidationCode.INVALID_FORMAT, "rules[1].actions[1] must not be blank"),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a policy without rules, without asking Altinn`() = testServer(altinn()) {
        for (body in listOf("{}", """{"rules":[]}""")) {
            putPolicy(body).assertProblem(validationError(FieldError("rules", ValidationCode.MISSING, "rules is required")))
        }

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 for a body with a field this API does not have`() = testServer(altinn()) {
        putPolicy("""{"rules":[{"subjects":["urn:altinn:rolecode:dagl"],"actions":["read"],"effect":"Deny"}]}""").assertProblem(
            ErrorResponse.of(
                ProblemType.MALFORMED_REQUEST,
                HttpStatusCode.BadRequest,
                "The body must be a JSON object with only this API's fields, each of the right type",
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 400 when the id is not valid, without asking Altinn`() = testServer(altinn()) {
        putPolicy(id = "KV-eksempel").assertProblem(
            validationError(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id, without writing to Altinn`() = testServer(altinn(resource = null)) {
        putPolicy().assertProblem(notFound)

        assertNothingWritten()
    }

    @Test
    fun `gives 404 when the resource is not a GenericAccessResource, without writing to Altinn`() =
        testServer(altinn(resource = resourceJson())) {
            putPolicy().assertProblem(notFound)

            assertNothingWritten()
        }

    @Test
    fun `gives 404 when the resource is not Kartverket's, without writing to Altinn`() =
        testServer(altinn(resource = genericAccessResource.replace("971040238", "974760673"))) {
            putPolicy().assertProblem(notFound)

            assertNothingWritten()
        }

    @Test
    fun `gives 400 with Altinn's words when Altinn rejects the policy`() = testServer(
        altinn(status = 400, body = "\"Policy not accepted: Contains rule without reference to registry resource id\""),
    ) {
        putPolicy().assertProblem(
            ErrorResponse.of(
                ProblemType.UPSTREAM_REJECTED,
                HttpStatusCode.BadRequest,
                "Altinn rejected the resource: Policy not accepted: Contains rule without reference to registry resource id",
            ),
        )
    }

    @Test
    fun `gives 502 when Altinn answers the upload with an error`() = testServer(altinn(status = 500, body = "boom")) {
        putPolicy().assertProblem(upstreamError)
    }
}
