package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

/** A resource in Altinn's Resource Registry, with the fields this library reads. Texts are keyed by language. */
@Serializable
public data class AltinnResource(
    public val identifier: String,
    public val resourceType: String? = null,
    public val title: Map<String, String> = emptyMap(),
    public val description: Map<String, String> = emptyMap(),
    public val rightDescription: Map<String, String> = emptyMap(),
    public val homepage: String? = null,
    public val status: String? = null,
    public val contactPoints: List<ContactPoint> = emptyList(),
    public val keywords: List<Keyword> = emptyList(),
    public val resourceReferences: List<ResourceReference> = emptyList(),
    public val delegable: Boolean,
    public val visible: Boolean,
) {
    /** The Maskinporten scopes the resource points to. */
    public val scopes: List<String>
        get() = resourceReferences.filter { it.referenceType == MASKINPORTEN_SCOPE }.mapNotNull { it.reference }

    internal companion object {
        const val MASKINPORTEN_SCHEMA = "MaskinportenSchema"
        const val MASKINPORTEN_SCOPE = "MaskinportenScope"
    }
}

@Serializable
public data class ResourceReference(
    public val reference: String? = null,
    public val referenceType: String? = null,
    public val referenceSource: String? = null,
)

@Serializable
public data class ContactPoint(
    public val category: String? = null,
    public val email: String? = null,
    public val telephone: String? = null,
    public val contactPage: String? = null,
)

@Serializable
public data class Keyword(
    public val word: String? = null,
    public val language: String? = null,
)
