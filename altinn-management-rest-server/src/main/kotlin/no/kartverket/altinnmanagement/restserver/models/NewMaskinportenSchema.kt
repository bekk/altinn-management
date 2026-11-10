package no.kartverket.altinnmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable

// The fields are nullable so a missing one is a validation error that names it, not a body that cannot be read
@Serializable
@JsonSchema.Required("resourceId", "scopes", "title", "description", "delegationDescription")
@JsonSchema.Description(
    "A new resource of type `MaskinportenSchema` for Altinn. This API makes Kartverket its owner, and sets " +
        "`delegable` and `visible` to `true`. `title`, `description` and `delegationDescription` must each have `en`, " +
        "`nb` and `nn`.",
)
data class NewMaskinportenSchema(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "The resource's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It must not be taken by any resource in " +
            "Altinn, of any owner, and it cannot be changed later.",
    )
    val resourceId: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "The Maskinporten scopes the resource is for: at least one, each without whitespace. Altinn may refuse " +
            "scopes that do not start with Kartverket's prefix, `kartverk:`.",
    )
    val scopes: List<String>? = null,

    val title: MultilingualText? = null,

    val description: MultilingualText? = null,

    val delegationDescription: MultilingualText? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A web page about the resource.")
    val homepage: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Where to get help with the resource. Left out, it is empty.")
    val contactPoints: List<ContactPoint>? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description("Words to find the resource by in Altinn. Left out, it is empty.")
    val keywords: List<Keyword>? = null,

    // The same values as the client's ResourceStatus, since an annotation cannot read them from it
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Enum("Completed", "Active", "UnderDevelopment", "Deprecated", "Withdrawn")
    @JsonSchema.Description("Altinn's status for the resource. Left out, the resource has no status, as in Altinn.")
    val status: String? = null,
)
