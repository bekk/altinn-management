package no.kartverket.altinnmanagement.mock

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

private const val ACCESS_LIST_PATH = "/resourceregistry/api/v1/access-lists"

private val READ_SCOPES = setOf(
    "altinn:resourceregistry/resource.admin",
    "altinn:resourceregistry/accesslist.read",
    "altinn:resourceregistry/accesslist.write",
    "altinn:resourceregistry/pdp:accesslist.read",
)

// These may read any owner's lists
private val OWNER_EXEMPT_SCOPES = setOf("altinn:resourceregistry/resource.admin", "altinn:resourceregistry/pdp:accesslist.read")

/**
 * Altinn's routes to read access lists and to read and change their members. Unlike resources, they need an Altinn
 * token for the owner in the path, with one of [READ_SCOPES] to read and one of [WRITE_SCOPES] to change. The mock's
 * tokens are all Kartverket's, so the owner must be `kv`.
 */
fun Route.accessLists(lists: AccessLists, tokens: Tokens) {
    get("$ACCESS_LIST_PATH/{owner}") {
        val owner = call.reader(tokens) ?: return@get
        val from = call.request.queryParameters["token"]?.let { raw ->
            decodeToken(raw) ?: return@get call.validationProblem(mapOf("token" to listOf("The value '$raw' is not valid.")))
        }
        val (page, next) = if (owner == KARTVERKET_ORGCODE) lists.page(from) else emptyList<Nothing>() to null
        val body = buildJsonObject {
            // Altinn leaves out a null, so the last page has no next
            putJsonObject("links") { next?.let { put("next", call.nextPageUrl("$ACCESS_LIST_PATH/$owner", it)) } }
            put("data", JsonArray(page))
        }
        call.respondJson(HttpStatusCode.OK, body)
    }

    get("$ACCESS_LIST_PATH/{owner}/{identifier}") {
        val owner = call.reader(tokens) ?: return@get
        val list = if (owner == KARTVERKET_ORGCODE) lists.get(call.pathParameters["identifier"]!!) else null
        if (list == null) return@get call.notFound()
        call.respondJson(HttpStatusCode.OK, list)
    }

    get("$ACCESS_LIST_PATH/{owner}/{identifier}/members") {
        val owner = call.reader(tokens) ?: return@get
        val id = call.pathParameters["identifier"]!!
        if (owner != KARTVERKET_ORGCODE || lists.get(id) == null) return@get call.notFound()
        val from = call.request.queryParameters["token"]?.let { raw ->
            decodeToken(raw) ?: return@get call.validationProblem(mapOf("token" to listOf("The value '$raw' is not valid.")))
        }
        call.respondMembers(lists.members, owner, id, from)
    }

    post("$ACCESS_LIST_PATH/{owner}/{identifier}/members") { changeMembers(lists, tokens, AccessListMembers::add) }

    delete("$ACCESS_LIST_PATH/{owner}/{identifier}/members") { changeMembers(lists, tokens, AccessListMembers::remove) }
}

// Altinn checks the count first, then the list, then looks every party up, and changes nothing when one is unknown
private suspend fun RoutingContext.changeMembers(
    lists: AccessLists,
    tokens: Tokens,
    change: AccessListMembers.(String, Collection<Party>) -> Unit,
) {
    val owner = call.listWriter(tokens) ?: return
    val urns = call.receivePartyUrns() ?: return
    if (urns.size > MAX_MEMBERS) {
        return call.altinnValidationProblem("RR.VLD-00001", "Cannot add or remove more than $MAX_MEMBERS members at a time.", "/data")
    }
    val id = call.pathParameters["identifier"]!!
    if (lists.get(id) == null) return call.notFound()
    val parties = urns.map { lists.members.party(it) ?: return call.partyNotFound() }
    lists.members.change(id, parties)
    call.respondMembers(lists.members, owner, id, from = null)
}

private const val MAX_MEMBERS = 100

private suspend fun ApplicationCall.respondMembers(members: AccessListMembers, owner: String, id: String, from: String?) {
    val (page, next) = members.page(id, from)
    val body = buildJsonObject {
        putJsonObject("links") { next?.let { put("next", nextPageUrl("$ACCESS_LIST_PATH/$owner/$id/members", it)) } }
        put("data", JsonArray(page))
    }
    respondJson(HttpStatusCode.OK, body)
}

/** The party URNs in the body `{"data": [...]}`, or null after answering 400 when Altinn could not read them. */
private suspend fun ApplicationCall.receivePartyUrns(): List<PartyUrn>? {
    val data = runCatching { Json.parseToJsonElement(receiveText()) }.getOrNull()
        ?.let { (it as? JsonObject)?.get("data") as? JsonArray }
    val urns = data?.map { element -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(PartyUrn::parse) }
    if (urns == null || urns.any { it == null }) {
        validationProblem(mapOf("data" to listOf("The JSON value could not be converted to a party URN.")))
        return null
    }
    return urns.filterNotNull()
}

private suspend fun ApplicationCall.partyNotFound() = respondJson(
    HttpStatusCode.BadRequest,
    buildJsonObject {
        put("type", "urn:altinn:error:RR-00001")
        put("title", "One or more party references not found.")
        put("status", 400)
        put("code", "RR-00001")
        put("traceId", traceId())
    },
)

private suspend fun ApplicationCall.altinnValidationProblem(code: String, title: String, path: String) = respondJson(
    HttpStatusCode.BadRequest,
    buildJsonObject {
        put("type", "urn:altinn:error:STD-00000")
        put("title", "One or more validation errors occurred.")
        put("status", 400)
        put("code", "STD-00000")
        put(
            "validationErrors",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("code", code)
                        put("title", title)
                        put("paths", buildJsonArray { add(JsonPrimitive(path)) })
                    },
                )
            },
        )
        put("traceId", traceId())
    },
)

private const val KARTVERKET_ORGCODE = "kv"

/** The owner in the path in lower case, like Altinn turns an org code into its own, or null after answering 401 or 403. */
private suspend fun ApplicationCall.reader(tokens: Tokens): String? {
    val claims = bearerClaims(tokens)
    if (claims == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val scopes = claims.scopes()
    val owner = parameters["owner"]!!.lowercase()
    if (scopes.none { it in READ_SCOPES } || (owner != KARTVERKET_ORGCODE && scopes.none { it in OWNER_EXEMPT_SCOPES })) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return owner
}

private val WRITE_SCOPES = setOf("altinn:resourceregistry/resource.admin", "altinn:resourceregistry/accesslist.write")

/** Like [reader], but the token needs one of [WRITE_SCOPES], and only the owner's own lists can be changed. */
private suspend fun ApplicationCall.listWriter(tokens: Tokens): String? {
    val claims = bearerClaims(tokens)
    if (claims == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val owner = parameters["owner"]!!.lowercase()
    if (claims.scopes().none { it in WRITE_SCOPES } || owner != KARTVERKET_ORGCODE) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return owner
}

// Altinn's page token is the identifier or party uuid the next page starts at, as a JSON string in unpadded base64url
private fun encodeToken(identifier: String) =
    Base64.getUrlEncoder().withoutPadding().encodeToString(JsonPrimitive(identifier).toString().toByteArray())

private fun decodeToken(token: String): String? = try {
    (Json.parseToJsonElement(Base64.getUrlDecoder().decode(token).decodeToString()) as? JsonPrimitive)?.takeIf { it.isString }?.content
} catch (_: IllegalArgumentException) {
    null
} catch (_: SerializationException) {
    null
}

// Altinn writes the link to the next page as an absolute URL
private fun ApplicationCall.nextPageUrl(path: String, from: String): String {
    val origin = request.origin
    return "${origin.scheme}://${origin.serverHost}:${origin.serverPort}$path?token=${encodeToken(from)}"
}
