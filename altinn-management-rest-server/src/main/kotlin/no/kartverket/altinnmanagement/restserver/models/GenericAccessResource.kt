package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.GenericAccessResource as ClientGenericAccessResource

@Serializable
@JsonSchema.Description(
    "A resource of type `GenericAccessResource` in Altinn, with the values Altinn has. A field Altinn has no value " +
        "for is left out. Unlike a MaskinportenSchema, it has no Maskinporten scopes. `title` is its name, " +
        "`description` says what it gives access to, and `delegationDescription` explains the rights an " +
        "organisation gets when the resource is delegated to it.",
)
data class GenericAccessResource(
    @JsonSchema.Description("The resource's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It cannot be changed.")
    val resourceId: String,

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
) {
    companion object {
        fun from(resource: ClientGenericAccessResource) = GenericAccessResource(
            resourceId = resource.resourceId.value,
            title = resource.title?.let(MultilingualText::from),
            description = resource.description?.let(MultilingualText::from),
            delegationDescription = resource.delegationDescription?.let(MultilingualText::from),
            homepage = resource.homepage,
            contactPoints = resource.contactPoints?.map(ContactPoint::from),
            keywords = resource.keywords?.map(Keyword::from),
            status = resource.status,
            delegable = resource.delegable,
            visible = resource.visible,
        )
    }
}
