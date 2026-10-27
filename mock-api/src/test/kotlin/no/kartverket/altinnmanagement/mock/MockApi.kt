package no.kartverket.altinnmanagement.mock

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun mockApi(
    registry: ResourceRegistry = ResourceRegistry.seeded(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(registry) }
    block()
}

internal suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

/** A version of a MaskinportenSchema resource, as Altinn returns it. */
internal fun resource(
    id: String,
    versionId: Long,
    scope: String = "kartverk:$id",
    organization: String = "971040238",
    extra: Map<String, JsonElement> = emptyMap(),
): JsonObject = buildJsonObject {
    put("identifier", id)
    putJsonObject("title") { put("en", id); put("nb", id); put("nn", id) }
    put("resourceType", "MaskinportenSchema")
    put(
        "resourceReferences",
        buildJsonArray {
            add(buildJsonObject { put("referenceSource", "Default"); put("reference", scope); put("referenceType", "MaskinportenScope") })
        },
    )
    putJsonObject("hasCompetentAuthority") { put("organization", organization); put("orgcode", "kv") }
    extra.forEach { (key, value) -> put(key, value) }
    put("versionId", versionId)
    put("createdAt", "2026-01-0${versionId % 9 + 1}T00:00:00+00:00")
    put("updatedAt", "2026-02-0${versionId % 9 + 1}T00:00:00+00:00")
}
