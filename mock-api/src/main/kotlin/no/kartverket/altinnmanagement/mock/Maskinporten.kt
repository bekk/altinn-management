package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private class InvalidGrantException(message: String) : RuntimeException(message)

/**
 * Plays Maskinporten's token endpoint for JWT grants. It checks a grant's claims like Maskinporten does, but not its
 * signature, and gives any client the scopes it asks for, on behalf of Kartverket.
 */
class Maskinporten(private val tokens: Tokens, issuer: String = "http://localhost:8090/") {
    /** Ends with `/`, like Maskinporten's `https://maskinporten.no/`. A grant's `aud` must be this. */
    val issuer = issuer.removeSuffix("/") + "/"

    // jti of each grant used (or the whole grant, when it has no jti), until the grant expires, so no grant is used twice
    private val usedGrants = ConcurrentHashMap<String, Instant>()

    internal fun issueToken(grantType: String?, assertion: String?): JsonObject {
        if (grantType != GRANT_TYPE) invalidGrant("grant_type must be $GRANT_TYPE")
        val claims = assertion?.split('.')?.takeIf { it.size == 3 }?.let { decodeJson(it[1]) }
            ?: invalidGrant("assertion must be a JWT")
        if (claims["aud"]?.jsonPrimitive?.content != issuer) invalidGrant("aud must be $issuer")
        val clientId = claims["iss"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: invalidGrant("iss is required")
        val now = Instant.now()
        val issuedAt = claims["iat"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond) ?: invalidGrant("iat is required")
        if (Duration.between(issuedAt, now).abs() > MAX_CLOCK_SKEW) invalidGrant("iat must be within $MAX_CLOCK_SKEW of now")
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond) ?: invalidGrant("exp is required")
        if (expiresAt < now) invalidGrant("The grant has expired")
        if (Duration.between(issuedAt, expiresAt) > MAX_GRANT_LIFETIME) invalidGrant("exp can be at most $MAX_GRANT_LIFETIME after iat")
        val scope = claims["scope"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: invalidGrant("scope is required")
        // Last, so a grant refused for another reason is not used up
        val grantId = claims["jti"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: assertion
        usedGrants.values.removeIf { it < now }
        if (usedGrants.putIfAbsent(grantId, expiresAt) != null) invalidGrant("The grant has been used before")

        val tokenExpiresAt = now.plusSeconds(TOKEN_LIFETIME_SECONDS)
        val token = tokens.issueMaskinporten(
            buildJsonObject {
                put("iss", this@Maskinporten.issuer)
                put("client_amr", "private_key_jwt")
                put("token_type", "Bearer")
                put("scope", scope)
                putJsonObject("consumer") {
                    put("authority", "iso6523-actorid-upis")
                    put("ID", "0192:$KARTVERKET")
                }
                put("client_id", clientId)
                put("iat", now.epochSecond)
                put("exp", tokenExpiresAt.epochSecond)
                put("jti", UUID.randomUUID().toString())
            },
            tokenExpiresAt,
        )
        return buildJsonObject {
            put("access_token", token)
            put("token_type", "Bearer")
            put("expires_in", TOKEN_LIFETIME_SECONDS)
            put("scope", scope)
        }
    }

    companion object {
        const val TOKEN_LIFETIME_SECONDS = 120L
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"

        // Maskinporten's limits, from https://docs.digdir.no/docs/Maskinporten/maskinporten_protocol_jwtgrant
        private val MAX_CLOCK_SKEW = Duration.ofSeconds(10)
        private val MAX_GRANT_LIFETIME = Duration.ofSeconds(180)

        private fun invalidGrant(message: String): Nothing = throw InvalidGrantException(message)

        private fun decodeJson(part: String) = try {
            Json.parseToJsonElement(Base64.getUrlDecoder().decode(part).decodeToString()).jsonObject
        } catch (e: IllegalArgumentException) {
            invalidGrant("The JWT is not base64url-encoded JSON")
        }
    }
}

fun Route.maskinporten(maskinporten: Maskinporten) {
    post("/token") {
        val parameters = call.receiveParameters()
        try {
            val response = maskinporten.issueToken(parameters["grant_type"], parameters["assertion"])
            call.respondJson(HttpStatusCode.OK, response)
        } catch (e: InvalidGrantException) {
            application.log.warn("Refused a token request: ${e.message}")
            call.respondJson(
                HttpStatusCode.BadRequest,
                buildJsonObject {
                    put("error", "invalid_grant")
                    put("error_description", e.message)
                },
            )
        }
    }
}
