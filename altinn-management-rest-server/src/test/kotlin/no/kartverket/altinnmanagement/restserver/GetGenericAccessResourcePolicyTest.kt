package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
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

class GetGenericAccessResourcePolicyTest {

    private val sent = mutableListOf<OutgoingRequest>()

    // Keeps the policy it is given, as Altinn does, and answers 404 until it has one
    private fun altinn(resource: String? = resourceJson("GenericAccessResource"), policy: String? = null, policyStatus: Int = 200): OutgoingHttpClient {
        var stored = policy
        return OutgoingHttpClient { request ->
            sent += request
            val isPolicy = request.url.path.endsWith("/policy")
            when {
                resource == null -> response(404, "application/problem+json", """{"status":404}""")

                !isPolicy -> response(200, "application/json; charset=utf-8", resource)

                request.method == HttpMethod.PUT -> {
                    stored = request.body!!.decodeToString().substringAfter("\r\n\r\n").substringBeforeLast("\r\n--")
                    response(201, "", "")
                }

                policyStatus != 200 -> response(policyStatus, "text/plain", "boom")

                else -> stored?.let { response(200, "text/xml", it) } ?: response(404, "text/plain; charset=utf-8", "Unable to find requested policy")
            }
        }
    }

    private val geonorge = """
        {
          "minimumAuthenticationLevel": { "endUser": 4, "organisation": 3 },
          "rules": [
            { "subjects": [["urn:altinn:accesspackage:eksplisitt"]], "actions": ["access"], "effect": "Permit" },
            {
              "description": "Datasets",
              "subjects": [["urn:altinn:accesspackage:eksplisitt", "urn:altinn:rolecode:dagl"], ["urn:altinn:rolecode:lede"]],
              "actions": ["download"],
              "subResources": [{ "urn:kv:geonorge": "dataset" }],
              "effect": "Deny"
            }
          ]
        }
    """.trimIndent()

    private val notFound = ErrorResponse.of(
        ProblemType.NOT_FOUND,
        HttpStatusCode.NotFound,
        "There is no GenericAccessResource with this id, or it has no policy yet",
    )

    @Test
    fun `gives back the policy that was put`() = testServer(altinn()) {
        val put = client.put("/api/genericaccessresources/kv-matrikkel-brukernavn/policy") {
            contentType(ContentType.Application.Json)
            setBody(geonorge)
        }
        assertEquals(HttpStatusCode.NoContent, put.status)

        val response = client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(Json.parseToJsonElement(geonorge), Json.parseToJsonElement(response.bodyAsText()))
    }

    @Test
    fun `asks Altinn for the policy without a token`() = testServer(altinn(policy = policyXml())) {
        assertEquals(HttpStatusCode.OK, client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").status)

        val request = sent.last()
        assertEquals("https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn/policy", request.url.toString())
        assertEquals(mapOf("Accept" to "text/xml"), request.headers)
    }

    @Test
    fun `answers the levels as an empty object when the policy has none`() = testServer(altinn(policy = policyXml(levels = false))) {
        val body = Json.parseToJsonElement(client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").bodyAsText())

        assertEquals(Json.parseToJsonElement("""{"minimumAuthenticationLevel":{},"rules":[{"subjects":[["urn:altinn:rolecode:dagl"]],"actions":["read"],"effect":"Permit"}]}"""), body)
    }

    @Test
    fun `gives 404 when the resource has no policy yet`() = testServer(altinn()) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(notFound)
    }

    @Test
    fun `gives 404 when Altinn has no resource with the id`() = testServer(altinn(resource = null)) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(notFound)
    }

    @Test
    fun `gives 404 without asking for the policy when the resource is not a GenericAccessResource`() =
        testServer(altinn(resource = resourceJson(), policy = policyXml())) {
            client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(notFound)

            assertEquals(1, sent.size)
        }

    @Test
    fun `gives 404 without asking for the policy when the resource is not Kartverket's`() =
        testServer(altinn(resource = resourceJson("GenericAccessResource").replace("971040238", "974760673"), policy = policyXml())) {
            client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(notFound)

            assertEquals(1, sent.size)
        }

    @Test
    fun `gives 400 when the id is not valid, without asking Altinn`() = testServer(altinn()) {
        client.get("/api/genericaccessresources/KV-eksempel/policy").assertProblem(
            ErrorResponse.of(
                ProblemType.VALIDATION_ERROR,
                HttpStatusCode.BadRequest,
                errors = listOf(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
            ),
        )

        assertEquals(emptyList(), sent)
    }

    @Test
    fun `gives 502 when the policy has a part this API cannot show`() = testServer(altinn(policy = policyXml(effect = "NotApplicable"))) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(
            ErrorResponse.of(ProblemType.UNSUPPORTED_POLICY, HttpStatusCode.BadGateway),
        )
    }

    @Test
    fun `gives 502 when Altinn answers the policy with an error`() = testServer(altinn(policyStatus = 500)) {
        client.get("/api/genericaccessresources/kv-matrikkel-brukernavn/policy").assertProblem(upstreamError)
    }

    private fun policyXml(effect: String = "Permit", levels: Boolean = true): String {
        val string = "http://www.w3.org/2001/XMLSchema#string"
        fun match(function: String, value: String, attributeId: String, category: String) = """
            <xacml:AnyOf><xacml:AllOf><xacml:Match MatchId="urn:oasis:names:tc:xacml:$function">
            <xacml:AttributeValue DataType="$string">$value</xacml:AttributeValue>
            <xacml:AttributeDesignator AttributeId="$attributeId" Category="$category" DataType="$string" MustBePresent="false" />
            </xacml:Match></xacml:AllOf></xacml:AnyOf>
        """.trimIndent()
        val obligation = """
            <xacml:ObligationExpressions><xacml:ObligationExpression ObligationId="urn:altinn:obligation:authenticationLevel1" FulfillOn="Permit">
            <xacml:AttributeAssignmentExpression AttributeId="urn:altinn:obligation1-assignment1" Category="urn:altinn:minimum-authenticationlevel">
            <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#integer">3</xacml:AttributeValue>
            </xacml:AttributeAssignmentExpression></xacml:ObligationExpression></xacml:ObligationExpressions>
        """.trimIndent()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <xacml:Policy xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" PolicyId="urn:altinn:policyid:1" Version="1.0" RuleCombiningAlgId="urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-overrides">
            <xacml:Target />
            <xacml:Rule RuleId="urn:altinn:resource:kv-matrikkel-brukernavn:ruleid:1" Effect="$effect">
            <xacml:Target>
            ${match("3.0:function:string-equal-ignore-case", "dagl", "urn:altinn:rolecode", "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject")}
            ${match("1.0:function:string-equal", "kv-matrikkel-brukernavn", "urn:altinn:resource", "urn:oasis:names:tc:xacml:3.0:attribute-category:resource")}
            ${match("3.0:function:string-equal-ignore-case", "read", "urn:oasis:names:tc:xacml:1.0:action:action-id", "urn:oasis:names:tc:xacml:3.0:attribute-category:action")}
            </xacml:Target>
            </xacml:Rule>
            ${if (levels) obligation else ""}
            </xacml:Policy>
        """.trim()
    }
}
