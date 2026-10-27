package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskinportenTest {

    private suspend fun ApplicationTestBuilder.token(
        assertion: String = grant(),
        grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer",
    ): HttpResponse = client.submitForm(
        "/token",
        parameters {
            append("grant_type", grantType)
            append("assertion", assertion)
        },
    )

    @Test
    fun `a grant gets a token with the scope it asks for`() = mockApi {
        val response = token(grant(scope = "altinn:resourceregistry/resource.write"))
        val body = response.json().jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Bearer", body.getValue("token_type").jsonPrimitive.content)
        assertEquals("120", body.getValue("expires_in").jsonPrimitive.content)
        assertEquals("altinn:resourceregistry/resource.write", body.getValue("scope").jsonPrimitive.content)
    }

    @Test
    fun `the token is a JWT for Kartverket, with the scope and the client`() = mockApi {
        val token = token(grant(iss = "dev-client")).json().jsonObject.getValue("access_token").jsonPrimitive.content
        val claims = claimsOf(token)

        assertEquals("0192:$KARTVERKET", claims.getValue("consumer").jsonObject.getValue("ID").jsonPrimitive.content)
        assertEquals("altinn:resourceregistry/resource.write", claims.getValue("scope").jsonPrimitive.content)
        assertEquals("dev-client", claims.getValue("client_id").jsonPrimitive.content)
        assertEquals("http://localhost:8090/", claims.getValue("iss").jsonPrimitive.content)
    }

    @Test
    fun `a grant is refused as invalid_grant when it breaks one of Maskinporten's rules`() = mockApi {
        val now = Instant.now()
        val refused = mapOf(
            "grant_type must be" to token(grantType = "client_credentials"),
            "assertion must be a JWT" to token(assertion = "not-a-jwt"),
            "aud must be" to token(grant(aud = "https://maskinporten.no/")),
            "iss is required" to token(grant(iss = null)),
            "iat is required" to token(grant(iat = null)),
            "iat must be within" to token(grant(iat = now.minusSeconds(60))),
            "exp is required" to token(grant(exp = null)),
            "The grant has expired" to token(grant(iat = now, exp = now.minusSeconds(5))),
            "exp can be at most" to token(grant(iat = now, exp = now.plusSeconds(181))),
            "scope is required" to token(grant(scope = null)),
        )

        for ((reason, response) in refused) {
            val body = response.json().jsonObject
            assertEquals(HttpStatusCode.BadRequest, response.status, reason)
            assertEquals("invalid_grant", body.getValue("error").jsonPrimitive.content, reason)
            assertEquals(true, body.getValue("error_description").jsonPrimitive.content.startsWith(reason), "$reason: $body")
        }
    }

    @Test
    fun `a grant can only be used once`() = mockApi {
        val grant = grant()

        assertEquals(HttpStatusCode.OK, token(grant).status)
        assertEquals("The grant has been used before", token(grant).json().jsonObject.getValue("error_description").jsonPrimitive.content)
    }
}
