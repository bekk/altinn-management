package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import no.kartverket.altinnmanagement.client.http.Http

@Serializable
internal data class AltinnResource(
    val identifier: String? = null,
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
    val hasCompetentAuthority: AltinnCompetentAuthority? = null,
    val resourceType: String? = null,
    val accessListMode: String? = null,
) {
    val isMaskinportenSchema: Boolean get() = resourceType == MASKINPORTEN_SCHEMA

    val isGenericAccessResource: Boolean get() = resourceType == GENERIC_ACCESS_RESOURCE

    val isKartverkets: Boolean get() = hasCompetentAuthority?.organization == KARTVERKET_ORGANIZATION_NUMBER

    fun toMaskinportenSchema(id: ResourceId): MaskinportenSchema = MaskinportenSchema(
        resourceId = id,
        scopes = resourceReferences?.filter { it.referenceType == MASKINPORTEN_SCOPE }?.mapNotNull { it.reference },
        title = title?.let(::multilingualText),
        description = description?.let(::multilingualText),
        delegationDescription = rightDescription?.let(::multilingualText),
        homepage = homepage,
        contactPoints = contactPoints?.map { it.toContactPoint() },
        keywords = keywords?.map { it.toKeyword() },
        status = status,
        delegable = delegable,
        visible = visible,
    )

    fun toGenericAccessResource(id: ResourceId): GenericAccessResource = GenericAccessResource(
        resourceId = id,
        title = title?.let(::multilingualText),
        description = description?.let(::multilingualText),
        delegationDescription = rightDescription?.let(::multilingualText),
        homepage = homepage,
        contactPoints = contactPoints?.map { it.toContactPoint() },
        keywords = keywords?.map { it.toKeyword() },
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
) {
    fun toContactPoint() = ContactPoint(category = category, email = email, telephone = telephone, contactPage = contactPage)
}

@Serializable
internal data class AltinnKeyword(
    val word: String? = null,
    val language: String? = null,
) {
    fun toKeyword() = Keyword(word = word, language = language)
}

@Serializable
internal data class AltinnResourceReference(
    val referenceSource: String? = null,
    val reference: String? = null,
    val referenceType: String? = null,
)

@Serializable
internal data class AltinnCompetentAuthority(
    val organization: String? = null,
    val orgcode: String? = null,
)

internal fun newAltinnResource(schema: NewMaskinportenSchema) = AltinnResource(
    identifier = schema.resourceId.value,
    title = schema.title.toMap(),
    description = schema.description.toMap(),
    rightDescription = schema.delegationDescription.toMap(),
    homepage = schema.homepage,
    status = schema.status?.value,
    contactPoints = schema.contactPoints.map { it.toAltinn() },
    keywords = schema.keywords.map { it.toAltinn() },
    resourceReferences = schema.scopes.map {
        AltinnResourceReference(referenceSource = "Default", reference = it, referenceType = MASKINPORTEN_SCOPE)
    },
    delegable = true,
    visible = true,
    resourceType = MASKINPORTEN_SCHEMA,
    hasCompetentAuthority = KARTVERKET_AUTHORITY,
    accessListMode = ACCESS_LIST_MODE_DISABLED,
)

internal fun newAltinnResource(resource: NewGenericAccessResource) = AltinnResource(
    identifier = resource.resourceId.value,
    title = resource.title.toMap(),
    description = resource.description.toMap(),
    rightDescription = resource.delegationDescription?.toMap(),
    homepage = resource.homepage,
    status = resource.status?.value,
    contactPoints = resource.contactPoints.map { it.toAltinn() },
    keywords = resource.keywords.map { it.toAltinn() },
    delegable = resource.delegable,
    visible = true,
    resourceType = GENERIC_ACCESS_RESOURCE,
    hasCompetentAuthority = KARTVERKET_AUTHORITY,
    accessListMode = ACCESS_LIST_MODE_DISABLED,
)

internal const val MASKINPORTEN_SCHEMA = "MaskinportenSchema"

internal const val GENERIC_ACCESS_RESOURCE = "GenericAccessResource"

internal const val KARTVERKET_ORGANIZATION_NUMBER = "971040238"

internal const val KARTVERKET_ORGCODE = "kv"

private val KARTVERKET_AUTHORITY = AltinnCompetentAuthority(organization = KARTVERKET_ORGANIZATION_NUMBER, orgcode = KARTVERKET_ORGCODE)

private const val ACCESS_LIST_MODE_DISABLED = "Disabled"

private const val MASKINPORTEN_SCOPE = "MaskinportenScope"

/**
 * The resource as Altinn has it, with [content] in place of this API's fields. Altinn replaces the whole resource on a
 * change, so every other field, and every reference that is not a Maskinporten scope, is kept as it is.
 */
internal fun JsonObject.withContent(content: MaskinportenSchemaContent): JsonObject {
    val scopes = content.scopes.map {
        Http.json.encodeToJsonElement(AltinnResourceReference.serializer(), AltinnResourceReference("Default", it, MASKINPORTEN_SCOPE))
    }
    val otherReferences = (this["resourceReferences"] as? JsonArray).orEmpty()
        .filter { (it as? JsonObject)?.get("referenceType") != JsonPrimitive(MASKINPORTEN_SCOPE) }
    val changed = withFields(
        title = content.title,
        description = content.description,
        delegationDescription = content.delegationDescription,
        homepage = content.homepage,
        contactPoints = content.contactPoints,
        keywords = content.keywords,
    )
    return JsonObject(changed + ("resourceReferences" to JsonArray(scopes + otherReferences)))
}

/**
 * The resource as Altinn has it, with [content] in place of this API's fields. Altinn replaces the whole resource on a
 * change, so every other field is kept as it is, the resource references and access list mode too.
 */
internal fun JsonObject.withContent(content: GenericAccessResourceContent): JsonObject = withFields(
    title = content.title,
    description = content.description,
    delegationDescription = content.delegationDescription,
    homepage = content.homepage,
    contactPoints = content.contactPoints,
    keywords = content.keywords,
)

// A null value is not encoded, so that field is removed from the resource
private fun JsonObject.withFields(
    title: MultilingualText,
    description: MultilingualText,
    delegationDescription: MultilingualText?,
    homepage: String?,
    contactPoints: List<ContactPoint>,
    keywords: List<Keyword>,
): JsonObject {
    val changes = Http.json.encodeToJsonElement(
        AltinnResource.serializer(),
        AltinnResource(
            title = title.toMap(),
            description = description.toMap(),
            rightDescription = delegationDescription?.toMap(),
            homepage = homepage,
            contactPoints = contactPoints.map { it.toAltinn() },
            keywords = keywords.map { it.toAltinn() },
        ),
    ).jsonObject
    return JsonObject(filterKeys { it !in REPLACED_FIELDS } + changes)
}

internal fun JsonObject.withdrawn(): JsonObject = JsonObject(
    filterKeys { it !in VERSION_FIELDS } + mapOf(
        "delegable" to JsonPrimitive(false),
        "visible" to JsonPrimitive(false),
        "status" to JsonPrimitive("Withdrawn"),
    ),
)

private val VERSION_FIELDS = setOf("versionId", "createdAt", "updatedAt")

private val REPLACED_FIELDS = setOf(
    "title", "description", "rightDescription", "homepage", "contactPoints", "keywords",
) + VERSION_FIELDS

private fun ContactPoint.toAltinn() =
    AltinnContactPoint(category = category, email = email, telephone = telephone, contactPage = contactPage)

private fun Keyword.toAltinn() = AltinnKeyword(word = word, language = language)

private fun MultilingualText.toMap() = mapOf("en" to en, "nb" to nb, "nn" to nn).filterValues { it != null }

private fun multilingualText(texts: Map<String, String?>) = MultilingualText(en = texts["en"], nb = texts["nb"], nn = texts["nn"])
