package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

// Loose on purpose: it reads any organisation's resource, and only the owner's MaskinportenSchemas are parsed further
@Serializable
internal data class AltinnResource(
    val identifier: String? = null,
    val title: Map<String, String?> = emptyMap(),
    val description: Map<String, String?> = emptyMap(),
    val rightDescription: Map<String, String?> = emptyMap(),
    val homepage: String? = null,
    val status: String? = null,
    val contactPoints: List<AltinnContactPoint> = emptyList(),
    val keywords: List<AltinnKeyword> = emptyList(),
    val resourceReferences: List<AltinnResourceReference> = emptyList(),
    val delegable: Boolean? = null,
    val visible: Boolean? = null,
    val hasCompetentAuthority: AltinnCompetentAuthority? = null,
    val resourceType: String? = null,
) {
    fun isMaskinportenSchemaOf(owner: OrganizationNumber): Boolean =
        resourceType == MASKINPORTEN_SCHEMA && hasCompetentAuthority?.organization == owner.value

    fun toMaskinportenSchema(): MaskinportenSchema = MaskinportenSchema(
        resourceId = ResourceId.of(required(identifier, "identifier")),
        scopes = resourceReferences
            .filter { it.referenceType == MASKINPORTEN_SCOPE }
            .map { Scope.of(required(it.reference, "reference in a MaskinportenScope reference")) },
        title = multilingualText(title, "title"),
        description = multilingualText(description, "description"),
        delegationDescription = multilingualText(rightDescription, "rightDescription"),
        homepage = homepage.present()?.let(WebAddress::of),
        contactPoints = contactPoints.map { it.toContactPoint() },
        keywords = keywords.map { it.toKeyword() },
        status = status.present()?.let(ResourceStatus::fromAltinn),
        delegable = required(delegable, "delegable"),
        visible = required(visible, "visible"),
    )
}

@Serializable
internal data class AltinnContactPoint(
    val category: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val contactPage: String? = null,
) {
    fun toContactPoint() = ContactPoint(
        category = category.present()?.let(ContactCategory::of),
        email = email.present()?.let(EmailAddress::of),
        telephone = telephone.present()?.let(PhoneNumber::of),
        contactPage = contactPage.present()?.let(WebAddress::of),
    )
}

@Serializable
internal data class AltinnKeyword(
    val word: String? = null,
    val language: String? = null,
) {
    fun toKeyword() = Keyword(
        word = KeywordText.of(required(word, "keyword word")),
        language = Language.of(required(language, "keyword language")),
    )
}

@Serializable
internal data class AltinnResourceReference(
    val reference: String? = null,
    val referenceType: String? = null,
)

@Serializable
internal data class AltinnCompetentAuthority(
    val organization: String? = null,
)

private const val MASKINPORTEN_SCHEMA = "MaskinportenSchema"

private const val MASKINPORTEN_SCOPE = "MaskinportenScope"

private fun multilingualText(texts: Map<String, String?>, field: String) = MultilingualText(
    en = EnglishText.of(required(texts["en"], "$field.en")),
    nb = BokmalText.of(required(texts["nb"], "$field.nb")),
    nn = NynorskText.of(required(texts["nn"], "$field.nn")),
)

private fun <T : Any> required(value: T?, field: String): T = requireNotNull(value) { "The resource has no $field" }

private fun String?.present(): String? = this?.takeIf { it.isNotBlank() }
