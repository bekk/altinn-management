package no.kartverket.altinnmanagement.mock

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private class Version(val versionId: Long, val resource: JsonObject, val createdAt: String, val updatedAt: String)

/**
 * Altinn's Resource Registry in memory. Like Altinn, it keeps every version of a resource: a version is a resource as
 * Altinn returns it, with its `versionId`, `createdAt` and `updatedAt`.
 */
class ResourceRegistry(versions: List<JsonObject>) {
    // A write replaces a resource's list instead of changing it, so a read without the lock still sees a whole list
    private val versions: MutableMap<String, List<Version>> = versions
        .map { Version(it.long("versionId"), it - METADATA, it.string("createdAt"), it.string("updatedAt")) }
        .groupBy { it.resource.string("identifier") }
        .mapValuesTo(ConcurrentHashMap()) { (_, versions) -> versions.sortedBy { it.versionId } }

    // Like Altinn's, a version id counts up across all resources
    private val lastVersionId = AtomicLong(versions.maxOfOrNull { it.long("versionId") } ?: 0)

    /** The newest version of [id], or the version [versionId] of it. The id must match exactly, case included. */
    fun get(id: String, versionId: Long? = null): JsonObject? {
        val versions = versions[id] ?: return null
        val version = if (versionId == null) versions.last() else versions.find { it.versionId == versionId }
        return version?.let { withMetadata(id, it) }
    }

    /** Adds [resource] as a new resource, or returns false when its identifier is taken. */
    fun create(resource: JsonObject): Boolean = synchronized(this) {
        val id = resource.string("identifier")
        if (id in versions) return false
        val now = now()
        versions[id] = listOf(Version(lastVersionId.incrementAndGet(), resource, now, now))
        true
    }

    /** Adds [resource] as the newest version of the resource with its identifier, which must exist. */
    fun update(resource: JsonObject) = synchronized(this) {
        val id = resource.string("identifier")
        val now = now()
        versions[id] = versions.getValue(id) + Version(lastVersionId.incrementAndGet(), resource, now, now)
    }

    /**
     * Like Altinn, it filters every version, and keeps the newest version of each resource that matches. So when only
     * an older version matches, that older version is returned.
     */
    fun search(filter: SearchFilter): List<JsonObject> = versions.keys.sorted().mapNotNull { id ->
        versions.getValue(id).lastOrNull { filter.matches(it.resource) }?.let { version ->
            withMetadata(id, version) + ("authorizationReference" to authorizationReference(id))
        }
    }

    private fun withMetadata(id: String, version: Version) = buildJsonObject {
        version.resource.forEach { (key, value) -> put(key, value) }
        put("versionId", version.versionId)
        put("createdAt", versions.getValue(id).first().createdAt)
        put("updatedAt", version.updatedAt)
    }

    companion object {
        private val METADATA = setOf("versionId", "createdAt", "updatedAt")

        /** Kartverket's three MaskinportenSchema resources in production, and one of another organisation's. */
        fun seeded(): ResourceRegistry {
            val json = ResourceRegistry::class.java.getResource("/resources.json")!!.readText()
            return ResourceRegistry(Json.parseToJsonElement(json).jsonArray.map { it.jsonObject })
        }
    }
}

/**
 * The query parameters of Altinn's search. A blank value is no filter, and a resource must match them all. Altinn
 * accepts `title` too, but ignores it.
 */
class SearchFilter(
    private val id: String? = null,
    private val resourceType: String? = null,
    private val orgCode: String? = null,
    private val organizationId: String? = null,
    private val description: String? = null,
    private val keyword: String? = null,
    private val reference: String? = null,
) {
    fun matches(resource: JsonObject): Boolean {
        val authority = resource["hasCompetentAuthority"]?.jsonObject
        return contains(resource.stringOrNull("identifier"), id) &&
            equals(resource.stringOrNull("resourceType"), resourceType) &&
            equals(authority?.stringOrNull("orgcode"), orgCode) &&
            equals(authority?.stringOrNull("organization"), organizationId) &&
            anyContains(resource["description"]?.jsonObject?.values?.map { it.jsonPrimitive.contentOrNull }, description) &&
            anyContains(resource.objects("keywords")?.map { it.stringOrNull("word") }, keyword) &&
            anyContains(resource.objects("resourceReferences")?.map { it.stringOrNull("reference") }, reference)
    }

    private fun contains(value: String?, filter: String?) = filter == null || value?.contains(filter, ignoreCase = true) == true

    private fun equals(value: String?, filter: String?) = filter == null || value.equals(filter, ignoreCase = true)

    private fun anyContains(values: List<String?>?, filter: String?) =
        filter == null || values.orEmpty().any { it?.contains(filter, ignoreCase = true) == true }
}

/** Altinn's resource types, each with its number. */
internal val RESOURCE_TYPES = listOf(
    "Default", "Systemresource", "MaskinportenSchema", "Altinn2Service", "AltinnApp", "GenericAccessResource",
    "BrokerService", "CorrespondenceService", "Consent", "MigratedApp",
).mapIndexed { index, name -> name to if (index == 0) 0 else 1 shl (index - 1) }

// Like Altinn's: microseconds, and an offset of +00:00
private fun now(): String = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx"))

private fun authorizationReference(id: String) = buildJsonArray {
    add(buildJsonObject { put("id", "urn:altinn:resource"); put("value", id) })
}

private operator fun JsonObject.plus(entry: Pair<String, JsonArray>) = JsonObject(this + mapOf(entry))

private operator fun JsonObject.minus(keys: Set<String>) = JsonObject(filterKeys { it !in keys })

private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

private fun JsonObject.stringOrNull(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.objects(key: String) = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>()
