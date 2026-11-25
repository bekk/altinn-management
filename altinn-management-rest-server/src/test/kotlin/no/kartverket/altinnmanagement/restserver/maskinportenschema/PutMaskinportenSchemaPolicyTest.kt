package no.kartverket.altinnmanagement.restserver.maskinportenschema

import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import no.kartverket.altinnmanagement.restserver.TEST_TOKEN
import no.kartverket.altinnmanagement.restserver.errorResponse
import no.kartverket.altinnmanagement.restserver.maskinportenSchemaNotFound
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PutMaskinportenSchemaPolicyTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private val path = "/api/maskinportenschemas/kv-matrikkel-brukernavn/policy"

    private fun altinn(resource: String? = resourceJson(), status: Int = 201, body: String = "") = OutgoingHttpClient { request ->
        sent += request
        when {
            request.method != HttpMethod.GET -> response(status, "application/json; charset=utf-8", body)
            resource == null -> response(404, "application/problem+json", """{"status":404}""")
            else -> response(200, "application/json; charset=utf-8", resource)
        }
    }

    private suspend fun HttpResponse.assertProblem(expected: ErrorResponse) {
        assertEquals(expected.status, status.value)
        assertEquals(ContentType.Application.ProblemJson, contentType()?.withoutParameters())
        assertEquals(expected, errorResponse())
    }

    private fun assertNothingWritten() = assertTrue(sent.all { it.method == HttpMethod.GET }, "Altinn got a write: $sent")

    @Test
    fun `answers 204 with no body`() = testServer(altinn()) {
        val response = client.put(path)

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `puts the policy to Altinn as the form file policyFile, with an Altinn token`() = testServer(altinn()) {
        client.put(path)

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
        val body = request.body!!.decodeToString()
        assertTrue(
            body.startsWith(
                "--AltinnManagementPolicyBoundary\r\n" +
                    "Content-Disposition: form-data; name=\"policyFile\"; filename=\"policy.xml\"\r\n" +
                    "Content-Type: application/xml\r\n" +
                    "\r\n" +
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<xacml:Policy ",
            ),
            body,
        )
        assertTrue(body.endsWith("</xacml:Policy>\r\n--AltinnManagementPolicyBoundary--\r\n"), body)
    }

    @Test
    fun `gives a policy that lets the access package maskinporten-scopes use the resource's scopes at security level 3`() =
        testServer(altinn()) {
            client.put(path)

            val policy = sent.single { it.method != HttpMethod.GET }.body!!.decodeToString()
            val value = """<xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">"""
            assertTrue("${value}maskinporten-scopes</xacml:AttributeValue>" in policy, policy)
            assertTrue("${value}kv-matrikkel-brukernavn</xacml:AttributeValue>" in policy, policy)
            assertTrue("${value}ScopeAccess</xacml:AttributeValue>" in policy, policy)
            assertTrue(
                """Category="urn:altinn:minimum-authenticationlevel">""" in policy &&
                    """<xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#integer">3</xacml:AttributeValue>""" in policy,
                policy,
            )
        }

    @Test
    fun `gives 404 when Altinn has no resource with the id, without writing to Altinn`() = testServer(altinn(resource = null)) {
        client.put(path).assertProblem(maskinportenSchemaNotFound)

        assertNothingWritten()
    }

    @Test
    fun `gives 400 when the id is not valid, without asking Altinn`() = testServer(altinn()) {
        client.put("/api/maskinportenschemas/KV-eksempel/policy").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
            ),
        )

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
