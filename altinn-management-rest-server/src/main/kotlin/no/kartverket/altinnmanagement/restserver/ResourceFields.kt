package no.kartverket.altinnmanagement.restserver

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.ContactPoint as ClientContactPoint
import no.kartverket.altinnmanagement.client.Keyword as ClientKeyword
import no.kartverket.altinnmanagement.client.MultilingualText as ClientMultilingualText

@Serializable
@JsonSchema.Description("A text in English, Norwegian Bokmål and Norwegian Nynorsk.")
data class MultilingualText(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In English. Left out if missing.")
    val en: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In Norwegian Bokmål. Left out if missing.")
    val nb: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("In Norwegian Nynorsk. Left out if missing.")
    val nn: String? = null,
) {
    companion object {
        fun from(text: ClientMultilingualText) = MultilingualText(en = text.en, nb = text.nb, nn = text.nn)
    }

    fun toClient() = ClientMultilingualText(en = en, nb = nb, nn = nn)
}

@Serializable
data class ContactPoint(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the contact point is, such as `Kundesenter`. Left out if missing.")
    val category: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("An email address. Left out, or empty, if missing.")
    val email: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A phone number. Left out, or empty, if missing.")
    val telephone: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page with contact information. Left out, or empty, if missing.")
    val contactPage: String? = null,
) {
    companion object {
        fun from(contactPoint: ClientContactPoint) = ContactPoint(
            category = contactPoint.category,
            email = contactPoint.email,
            telephone = contactPoint.telephone,
            contactPage = contactPoint.contactPage,
        )
    }

    fun toClient() = ClientContactPoint(category = category, email = email, telephone = telephone, contactPage = contactPage)
}

@Serializable
data class Keyword(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The word. Left out if missing.")
    val word: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The word's language, such as `nb`. Left out if missing.")
    val language: String? = null,
) {
    companion object {
        fun from(keyword: ClientKeyword) = Keyword(word = keyword.word, language = keyword.language)
    }

    fun toClient() = ClientKeyword(word = word, language = language)
}

@Serializable
@JsonSchema.Required("accessListMode")
@JsonSchema.Description("Whether only organisations on the resource's connected access lists get access to it.")
data class AccessListModeRequest(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Enum("Enabled", "Disabled")
    @JsonSchema.Description(
        "Required. `Enabled` lets only the organisations on the resource's connected access lists through, and " +
            "`Disabled` turns that off.",
    )
    val accessListMode: String? = null,
)
