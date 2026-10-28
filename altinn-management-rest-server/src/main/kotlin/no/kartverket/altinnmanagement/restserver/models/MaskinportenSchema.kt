package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.BokmalText
import no.kartverket.altinnmanagement.client.ContactCategory
import no.kartverket.altinnmanagement.client.EmailAddress
import no.kartverket.altinnmanagement.client.EnglishText
import no.kartverket.altinnmanagement.client.KeywordText
import no.kartverket.altinnmanagement.client.Language
import no.kartverket.altinnmanagement.client.NynorskText
import no.kartverket.altinnmanagement.client.PhoneNumber
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.ResourceStatus
import no.kartverket.altinnmanagement.client.Scope
import no.kartverket.altinnmanagement.client.WebAddress
import no.kartverket.altinnmanagement.client.ContactPoint as ClientContactPoint
import no.kartverket.altinnmanagement.client.Keyword as ClientKeyword
import no.kartverket.altinnmanagement.client.MaskinportenSchema as ClientMaskinportenSchema
import no.kartverket.altinnmanagement.client.MultilingualText as ClientMultilingualText

@Serializable
@JsonSchema.Description(
    "One of Kartverket's resources of type `MaskinportenSchema` in Altinn. `title` is its name, `description` says " +
        "what it gives access to, and `delegationDescription` explains the rights an organisation gets when the " +
        "resource is delegated to it.",
)
data class MaskinportenSchema(
    @JsonSchema.Description("The resource's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It cannot be changed.")
    val resourceId: ResourceId,

    @JsonSchema.Description(
        "The Maskinporten scopes the resource is for, at least one. An organisation the resource is delegated to in " +
            "Altinn can get tokens with these scopes on behalf of the organisation that delegated it.",
    )
    val scopes: List<Scope>,

    val title: MultilingualText,

    val description: MultilingualText,

    val delegationDescription: MultilingualText,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page about the resource, starting with `http://` or `https://`. Left out if it has none.")
    val homepage: WebAddress? = null,

    @JsonSchema.Description("Where to get help with the resource. Can be empty.")
    val contactPoints: List<ContactPoint>,

    @JsonSchema.Description("Words to find the resource by in Altinn. Can be empty.")
    val keywords: List<Keyword>,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        """Altinn's status for the resource. Left out if it has none.

- `COMPLETED`, `ACTIVE`: in use.
- `UNDER_DEVELOPMENT`: not finished.
- `DEPRECATED`: on its way out.
- `WITHDRAWN`: withdrawn. It cannot be delegated any more, and Altinn does not show it.
- `UNKNOWN`: a status this API does not know yet.""",
    )
    val status: ResourceStatus? = null,

    @JsonSchema.Description("If `true`, organisations can delegate the resource to others in Altinn.")
    val delegable: Boolean,

    @JsonSchema.Description("If `true`, Altinn shows the resource to its users.")
    val visible: Boolean,
) {
    companion object {
        fun from(schema: ClientMaskinportenSchema) = MaskinportenSchema(
            resourceId = schema.resourceId,
            scopes = schema.scopes,
            title = MultilingualText.from(schema.title),
            description = MultilingualText.from(schema.description),
            delegationDescription = MultilingualText.from(schema.delegationDescription),
            homepage = schema.homepage,
            contactPoints = schema.contactPoints.map(ContactPoint::from),
            keywords = schema.keywords.map(Keyword::from),
            status = schema.status,
            delegable = schema.delegable,
            visible = schema.visible,
        )
    }
}

@Serializable
@JsonSchema.Description("A text in English, Norwegian Bokmål and Norwegian Nynorsk.")
data class MultilingualText(
    @JsonSchema.Description("In English. Not blank.")
    val en: EnglishText,

    @JsonSchema.Description("In Norwegian Bokmål. Not blank.")
    val nb: BokmalText,

    @JsonSchema.Description("In Norwegian Nynorsk. Not blank.")
    val nn: NynorskText,
) {
    companion object {
        fun from(multilingualText: ClientMultilingualText) = MultilingualText(multilingualText.en, multilingualText.nb, multilingualText.nn)
    }
}

@Serializable
data class ContactPoint(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the contact point is, such as `Kundesenter`. Left out if it has none.")
    val category: ContactCategory? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("An email address. Left out if it has none.")
    val email: EmailAddress? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A phone number. Left out if it has none.")
    val telephone: PhoneNumber? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "A web page with contact information, starting with `http://` or `https://`. Left out if it has none.",
    )
    val contactPage: WebAddress? = null,
) {
    companion object {
        fun from(contactPoint: ClientContactPoint) =
            ContactPoint(contactPoint.category, contactPoint.email, contactPoint.telephone, contactPoint.contactPage)
    }
}

@Serializable
data class Keyword(
    @JsonSchema.Description("The word.")
    val word: KeywordText,

    @JsonSchema.Description("The word's language: `en`, `nb` or `nn`.")
    val language: Language,
) {
    companion object {
        fun from(keyword: ClientKeyword) = Keyword(keyword.word, keyword.language)
    }
}
