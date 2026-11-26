package no.kartverket.altinnmanagement.restserver.maskinportenschema

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.restserver.ContactPoint
import no.kartverket.altinnmanagement.restserver.Keyword
import no.kartverket.altinnmanagement.restserver.MultilingualText
import no.kartverket.altinnmanagement.client.MaskinportenSchema as ClientMaskinportenSchema

@Serializable
@JsonSchema.Description(
    "A resource of type `MaskinportenSchema` in Altinn, with the values Altinn has. A field Altinn has no value " +
        "for is left out. `title` is its name, `description` says what it gives access to, and " +
        "`delegationDescription` explains the rights an organisation gets when the resource is delegated to it.",
)
data class MaskinportenSchema(
    @JsonSchema.Description("The resource's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It cannot be changed.")
    val resourceId: String,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "The Maskinporten scopes the resource is for. An organisation the resource is delegated to in Altinn can get " +
            "tokens with these scopes on behalf of the organisation that delegated it.",
    )
    val scopes: List<String>? = null,

    val title: MultilingualText? = null,

    val description: MultilingualText? = null,

    val delegationDescription: MultilingualText? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page about the resource.")
    val homepage: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Where to get help with the resource. Can be empty.")
    val contactPoints: List<ContactPoint>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Words to find the resource by in Altinn. Can be empty.")
    val keywords: List<Keyword>? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "Altinn's status for the resource, such as `Completed`, `Active`, `UnderDevelopment`, `Deprecated` or " +
            "`Withdrawn`.",
    )
    val status: String? = null,

    @JsonSchema.Type(JsonType.BOOLEAN)
    @JsonSchema.Description("If `true`, organisations can delegate the resource to others in Altinn.")
    val delegable: Boolean? = null,

    @JsonSchema.Type(JsonType.BOOLEAN)
    @JsonSchema.Description("If `true`, Altinn shows the resource to its users.")
    val visible: Boolean? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "`Enabled` when only organisations on the resource's connected access lists get access to it, or `Disabled`. " +
            "Changed only with `PUT /api/maskinportenschemas/{id}/accesslistmode`, not by a `PUT` of the resource.",
    )
    val accessListMode: String? = null,
) {
    companion object {
        fun from(schema: ClientMaskinportenSchema) = MaskinportenSchema(
            resourceId = schema.resourceId.value,
            scopes = schema.scopes,
            title = schema.title?.let(MultilingualText::from),
            description = schema.description?.let(MultilingualText::from),
            delegationDescription = schema.delegationDescription?.let(MultilingualText::from),
            homepage = schema.homepage,
            contactPoints = schema.contactPoints?.map(ContactPoint::from),
            keywords = schema.keywords?.map(Keyword::from),
            status = schema.status,
            delegable = schema.delegable,
            visible = schema.visible,
            accessListMode = schema.accessListMode,
        )
    }
}
