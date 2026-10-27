package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AltinnAuthenticationTest {

    private val path = "/authentication/api/v1/exchange/maskinporten"

    private suspend fun ApplicationTestBuilder.maskinportenToken(scope: String = "altinn:resourceregistry/resource.write") =
        client.submitForm(
            "/token",
            parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", grant(scope = scope))
            },
        ).json().jsonObject.getValue("access_token").jsonPrimitive.content

    @Test
    fun `a Maskinporten token is exchanged for an Altinn token with its scope and consumer`() = mockApi {
        val maskinporten = claimsOf(maskinportenToken())
        val response = client.get(path) { header(HttpHeaders.Authorization, "Bearer ${maskinportenToken()}") }
        val altinn = claimsOf(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
        assertEquals(maskinporten.getValue("scope"), altinn.getValue("scope"))
        assertEquals(maskinporten.getValue("consumer"), altinn.getValue("consumer"))
        assertEquals(maskinporten.getValue("client_id"), altinn.getValue("client_id"))
    }

    @Test
    fun `the Altinn token has Altinn's own claims, and drops Maskinporten's issuer`() = mockApi {
        val altinn = claimsOf(client.get(path) { header(HttpHeaders.Authorization, "Bearer ${maskinportenToken()}") }.bodyAsText())

        assertEquals("http://localhost:8090/authentication/api/v1/openid/", altinn.getValue("iss").jsonPrimitive.content)
        assertEquals(KARTVERKET, altinn.getValue("urn:altinn:orgNumber").jsonPrimitive.content)
        assertEquals("maskinporten", altinn.getValue("urn:altinn:authenticatemethod").jsonPrimitive.content)
        assertEquals("3", altinn.getValue("urn:altinn:authlevel").jsonPrimitive.content)
        assertFalse("client_amr" in altinn)
        val lifetime = altinn.getValue("exp").jsonPrimitive.long - Instant.now().epochSecond
        assertTrue(lifetime in 1790..1800, "expected 30 minutes, got $lifetime seconds")
    }

    @Test
    fun `a caller that only accepts JSON gets the token as a JSON string`() = mockApi {
        val response = client.get(path) {
            header(HttpHeaders.Authorization, "Bearer ${maskinportenToken()}")
            header(HttpHeaders.Accept, "application/json")
        }

        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(response.bodyAsText().startsWith("\""), response.bodyAsText())
    }

    @Test
    fun `without a Maskinporten token the mock issued, the answer is 401`() = mockApi {
        val responses = listOf(
            client.get(path),
            client.get(path) { header(HttpHeaders.Authorization, "Bearer ") },
            client.get(path) { header(HttpHeaders.Authorization, "Bearer ${grant()}") },
        )

        for (response in responses) {
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
            assertEquals("Unauthorized", response.json().jsonObject.getValue("title").jsonPrimitive.content)
        }
    }
}
