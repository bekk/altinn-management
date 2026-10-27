package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyTest {

    private fun rule(vararg resources: String) = """
        <xacml:Rule RuleId="rule-${resources.joinToString("-")}" Effect="Permit">
          <xacml:Target>
            <xacml:AnyOf>
              <xacml:AllOf>
                ${resources.joinToString("\n") { resource ->
        """
                <xacml:Match MatchId="urn:oasis:names:tc:xacml:1.0:function:string-equal">
                  <xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">$resource</xacml:AttributeValue>
                  <xacml:AttributeDesignator AttributeId="urn:altinn:resource" Category="urn:oasis:names:tc:xacml:3.0:attribute-category:resource" DataType="http://www.w3.org/2001/XMLSchema#string" MustBePresent="false"/>
                </xacml:Match>"""
    }}
              </xacml:AllOf>
            </xacml:AnyOf>
          </xacml:Target>
        </xacml:Rule>
    """

    private fun policy(vararg rules: String) = """
        <?xml version="1.0" encoding="utf-8"?>
        <xacml:Policy xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" PolicyId="urn:altinn:policyid:1" Version="1.0"
            RuleCombiningAlgId="urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-overrides">
          <xacml:Target/>
          ${rules.joinToString("\n")}
        </xacml:Policy>
    """.trimIndent()

    private suspend fun ApplicationTestBuilder.upload(
        id: String,
        policy: String?,
        method: HttpMethod = HttpMethod.Post,
        token: String? = null,
    ): HttpResponse {
        val bearer = token ?: altinnToken()
        return client.submitFormWithBinaryData(
            "/resourceregistry/api/v1/resource/$id/policy",
            formData {
                if (policy != null) {
                    append(
                        "policyFile",
                        policy.toByteArray(),
                        Headers.build {
                            append(HttpHeaders.ContentType, "text/xml")
                            append(HttpHeaders.ContentDisposition, "filename=\"policy.xml\"")
                        },
                    )
                }
            },
        ) {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer $bearer")
        }
    }

    @Test
    fun `a policy whose rules are all about the resource is accepted, with POST and PUT`() = mockApi {
        val posted = upload("nrl-reporting", policy(rule("nrl-reporting")))
        val put = upload("nrl-reporting", policy(rule("nrl-reporting"), rule("nrl-reporting")), method = HttpMethod.Put)

        assertEquals(HttpStatusCode.Created, posted.status)
        assertEquals("nrl-reporting/policy", posted.headers[HttpHeaders.Location])
        assertEquals("", posted.bodyAsText())
        assertEquals(HttpStatusCode.Created, put.status)
    }

    @Test
    fun `a rule about another resource, or about none, is refused`() = mockApi {
        val other = upload("nrl-reporting", policy(rule("nrl-reporting"), rule("nrl-reporting", "nrl-retrieval")))
        val none = upload("nrl-reporting", policy(rule("nrl-reporting"), rule()))

        assertEquals(HttpStatusCode.BadRequest, other.status)
        assertEquals("\"Policy not accepted: Contains rules for a different registry resource\"", other.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, none.status)
        assertEquals("\"Policy not accepted: Contains rule without reference to registry resource id\"", none.bodyAsText())
    }

    @Test
    fun `a policy Altinn cannot read makes it fail`() = mockApi {
        assertEquals(HttpStatusCode.InternalServerError, upload("nrl-reporting", "not xml").status)
        assertEquals(HttpStatusCode.InternalServerError, upload("nrl-reporting", policy()).status, "a policy without rules")
    }

    @Test
    fun `without a file, or for an unknown resource, the policy is refused`() = mockApi {
        val noFile = upload("nrl-reporting", null)
        val unknown = upload("kv-nothing", policy(rule("kv-nothing")))

        assertEquals("\"The policy file can not be null\"", noFile.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals("\"Unknown resource\"", unknown.bodyAsText())
    }

    @Test
    fun `the owner Altinn has must be the token's, unless the token has the admin scope`() = mockApi {
        val forbidden = upload("slf-skogtiltakws", policy(rule("slf-skogtiltakws")))
        val asAdmin = upload("slf-skogtiltakws", policy(rule("slf-skogtiltakws")), token = altinnToken(scope = "altinn:resourceregistry/resource.admin"))

        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(HttpStatusCode.Created, asAdmin.status)
    }

    @Test
    fun `a policy needs a token with a scope to write`() = mockApi {
        assertEquals(HttpStatusCode.Unauthorized, upload("nrl-reporting", policy(rule("nrl-reporting")), token = "nope").status)
    }
}
