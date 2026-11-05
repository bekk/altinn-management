package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

@Serializable
internal data class AltinnResource(
    val title: Map<String, String?>? = null,
    val description: Map<String, String?>? = null,
    val rightDescription: Map<String, String?>? = null,
    val homepage: String? = null,
    val status: String? = null,
    val contactPoints: List<AltinnContactPoint>? = null,
    val keywords: List<AltinnKeyword>? = null,
    val resourceReferences: List<AltinnResourceReference>? = null,
    val delegable: Boolean? = null,
    val visible: Boolean? = null,
    val resourceType: String? = null,
) {
    val isMaskinportenSchema: Boolean get() = resourceType == MASKINPORTEN_SCHEMA

    fun toMaskinportenSchema(id: ResourceId): MaskinportenSchema = MaskinportenSchema(
        resourceId = id,
        scopes = resourceReferences?.filter { it.referenceType == MASKINPORTEN_SCOPE }?.mapNotNull { it.reference },
        title = title?.let(::multilingualText),
        description = description?.let(::multilingualText),
        delegationDescription = rightDescription?.let(::multilingualText),
        homepage = homepage,
        contactPoints = contactPoints?.map {
            ContactPoint(category = it.category, email = it.email, telephone = it.telephone, contactPage = it.contactPage)
        },
        keywords = keywords?.map { Keyword(word = it.word, language = it.language) },
        status = status,
        delegable = delegable,
        visible = visible,
    )
}

@Serializable
internal data class AltinnContactPoint(
    val category: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val contactPage: String? = null,
)

@Serializable
internal data class AltinnKeyword(
    val word: String? = null,
    val language: String? = null,
)

@Serializable
internal data class AltinnResourceReference(
    val reference: String? = null,
    val referenceType: String? = null,
)

private const val MASKINPORTEN_SCHEMA = "MaskinportenSchema"

private const val MASKINPORTEN_SCOPE = "MaskinportenScope"

private fun multilingualText(texts: Map<String, String?>) = MultilingualText(en = texts["en"], nb = texts["nb"], nn = texts["nn"])
