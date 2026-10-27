package no.kartverket.altinnmanagement.mock

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** Kartverket's organisation number. The mock issues every token for Kartverket. */
const val KARTVERKET = "971040238"

private class IssuedToken(val claims: JsonObject, val expiresAt: Instant)

/**
 * The tokens the mock has issued, and their claims. The mock's tokens are JWTs without a signature: instead of checking
 * a signature, the mock only accepts a token it issued itself, until it expires.
 */
class Tokens {
    private val maskinporten = ConcurrentHashMap<String, IssuedToken>()
    private val altinn = ConcurrentHashMap<String, IssuedToken>()

    fun issueMaskinporten(claims: JsonObject, expiresAt: Instant): String = issue(maskinporten, claims, expiresAt)

    fun issueAltinn(claims: JsonObject, expiresAt: Instant): String = issue(altinn, claims, expiresAt)

    /** The claims of a Maskinporten token the mock issued, or null when it did not issue it or it has expired. */
    fun maskinportenClaims(token: String): JsonObject? = claims(maskinporten, token)

    /** The claims of an Altinn token the mock issued, or null when it did not issue it or it has expired. */
    fun altinnClaims(token: String): JsonObject? = claims(altinn, token)

    private fun issue(tokens: MutableMap<String, IssuedToken>, claims: JsonObject, expiresAt: Instant): String {
        tokens.values.removeIf { it.expiresAt < Instant.now() }
        return unsignedJwt(claims).also { tokens[it] = IssuedToken(claims, expiresAt) }
    }

    private fun claims(tokens: Map<String, IssuedToken>, token: String) =
        tokens[token]?.takeIf { it.expiresAt >= Instant.now() }?.claims
}

private fun unsignedJwt(claims: JsonObject): String {
    val header = buildJsonObject { put("alg", "none"); put("typ", "JWT") }
    return "${base64Url(header)}.${base64Url(claims)}."
}

private fun base64Url(json: JsonObject) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray())
