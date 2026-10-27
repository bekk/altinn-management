package no.kartverket.altinnmanagement.mock

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.SecureRandom
import java.util.HexFormat

/** The read routes of Altinn's Resource Registry. Like in Altinn, anyone may read, with or without a token. */
fun Route.resourceRegistry(registry: ResourceRegistry) {
    get("/resourceregistry/api/v1/resource/{id}") {
        val id = call.pathParameters["id"]!!
        // Altinn's routes ignore case, and its fixed routes win over an id
        if (id.equals("search", ignoreCase = true)) return@get call.search(registry)

        val versionId = call.query("versionId")?.let { raw ->
            raw.toLongOrNull() ?: return@get call.validationProblem("versionId", "The value '$raw' is not valid.")
        }
        val resource = registry.get(id, versionId) ?: return@get call.notFound()
        call.respondJson(HttpStatusCode.OK, resource)
    }
}

private suspend fun ApplicationCall.search(registry: ResourceRegistry) {
    val filter = SearchFilter(
        id = query("id"),
        resourceType = query("resourceType"),
        orgCode = query("orgCode"),
        organizationId = query("organizationId"),
        description = query("description"),
        keyword = query("keyword"),
        reference = query("reference"),
    )
    respondJson(HttpStatusCode.OK, JsonArray(registry.search(filter)))
}

// Altinn binds query parameters whatever their case, and treats an empty value as none
private fun ApplicationCall.query(name: String): String? = request.queryParameters.entries()
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value?.firstOrNull()?.takeIf { it.isNotEmpty() }

// Altinn's ASP.NET answers its errors as ProblemDetails, but sends them as application/json like everything else
private suspend fun ApplicationCall.notFound() = respondJson(
    HttpStatusCode.NotFound,
    buildJsonObject {
        put("type", "https://tools.ietf.org/html/rfc9110#section-15.5.5")
        put("title", "Not Found")
        put("status", 404)
        put("traceId", traceId())
    },
)

private suspend fun ApplicationCall.validationProblem(field: String, message: String) = respondJson(
    HttpStatusCode.BadRequest,
    buildJsonObject {
        put("type", "https://tools.ietf.org/html/rfc9110#section-15.5.1")
        put("title", "One or more validation errors occurred.")
        put("status", 400)
        putJsonObject("errors") { put(field, buildJsonArray { add(JsonPrimitive(message)) }) }
        put("traceId", traceId())
    },
)

internal suspend fun ApplicationCall.respondJson(status: HttpStatusCode, body: JsonElement) =
    respondText(body.toString(), ContentType.Application.Json.withCharset(Charsets.UTF_8), status)

private val random = SecureRandom()

internal fun traceId(): String {
    fun hex(bytes: Int) = HexFormat.of().formatHex(ByteArray(bytes).also(random::nextBytes))
    return "00-${hex(16)}-${hex(8)}-00"
}
