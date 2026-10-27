package no.kartverket.altinnmanagement.mock

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

private const val ALTINN_TOKEN_LIFETIME_SECONDS = 30 * 60L

// Altinn drops these claims of the Maskinporten token, and sets its own iss and sid
private val DROPPED_CLAIMS = setOf("aud", "iss", "client_amr", "sid")

/**
 * Plays Altinn's exchange of a Maskinporten token for an Altinn token. Like Altinn, the Altinn token has the Maskinporten
 * token's claims, `scope` and `consumer` among them, and Altinn's own on top.
 */
fun Route.altinnAuthentication(tokens: Tokens, platformBaseUrl: String = "http://localhost:8090") {
    get("/authentication/api/v1/exchange/maskinporten") {
        val token = call.request.headers[HttpHeaders.Authorization]
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substring("Bearer ".length)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@get call.unauthorized()
        val claims = tokens.maskinportenClaims(token) ?: return@get call.unauthorized()

        val now = Instant.now()
        val expiresAt = now.plusSeconds(ALTINN_TOKEN_LIFETIME_SECONDS)
        val organizationNumber = claims.getValue("consumer").jsonObject.getValue("ID").jsonPrimitive.content.substringAfter(':')
        val altinnToken = unsignedJwt(
            buildJsonObject {
                claims.filterKeys { it !in DROPPED_CLAIMS }.forEach { (key, value) -> put(key, value) }
                put("urn:altinn:orgNumber", organizationNumber)
                put("urn:altinn:authenticatemethod", "maskinporten")
                put("urn:altinn:authlevel", 3)
                put("iss", "$platformBaseUrl/authentication/api/v1/openid/")
                put("sid", UUID.randomUUID().toString())
                put("iat", now.epochSecond)
                put("nbf", now.epochSecond)
                put("exp", expiresAt.epochSecond)
            },
        )
        call.respondToken(altinnToken)
    }
}

// ASP.NET sends the token as plain text, unless the caller only accepts JSON: then it is a JSON string
private suspend fun ApplicationCall.respondToken(token: String) {
    val accept = request.headers[HttpHeaders.Accept].orEmpty()
    val onlyJson = accept.contains("application/json") && !accept.contains("text/plain") && !accept.contains("*/*")
    if (onlyJson) {
        respondText(JsonPrimitive(token).toString(), ContentType.Application.Json.withCharset(Charsets.UTF_8))
    } else {
        respondText(token, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
    }
}

private suspend fun ApplicationCall.unauthorized() = respondText(
    buildJsonObject {
        put("type", "https://tools.ietf.org/html/rfc9110#section-15.5.2")
        put("title", "Unauthorized")
        put("status", 401)
        put("traceId", traceId())
    }.toString(),
    ContentType.Application.ProblemJson.withCharset(Charsets.UTF_8),
    HttpStatusCode.Unauthorized,
)
