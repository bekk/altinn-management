package no.kartverket.altinnmanagement.restserver

import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.MaskinportenSchema

@Serializable
data class AccessFilter(
    val scopes: Filter,
    val resources: Filter,
) {
    // All, not any: otherwise a caller would see a resource that gives a scope the caller may not see
    fun allows(schema: MaskinportenSchema): Boolean =
        resources.allows(schema.resourceId.value) && schema.scopes.orEmpty().all(scopes::allows)

    companion object {
        val NOTHING = AccessFilter(Filter(emptyList(), emptyList()), Filter(emptyList(), emptyList()))
    }
}

@Serializable
data class Filter(
    val exact: List<String>,
    val prefix: List<String>,
) {
    init {
        require(prefix.none(String::isBlank)) { "A prefix must not be blank" }
    }

    fun allows(name: String): Boolean = name in exact || prefix.any { name.startsWith(it) }
}

internal fun List<MaskinportenSchema>.allowedBy(filter: AccessFilter?): List<MaskinportenSchema> =
    if (filter == null) this else filter(filter::allows)
