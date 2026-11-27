package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable

// The fields are nullable so a missing one is a validation error that names it, not a body that cannot be read
@Serializable
@JsonSchema.Required("accessListId", "name")
@JsonSchema.Description("A new access list for Kartverket in Altinn.")
data class NewAccessList(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "The list's id in Altinn: 4 or more of `a-z`, `0-9`, `_` and `-`. It must not be taken by another of " +
            "Kartverket's lists, and it cannot be changed later.",
    )
    val accessListId: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The list's name.")
    val name: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the list is for. Left out, it is empty.")
    val description: String? = null,
)

@Serializable
@JsonSchema.Required("name")
@JsonSchema.Description(
    "A list's name and description, to replace those in Altinn. A description that is left out is emptied. The list's " +
        "members and the resources it is connected to are kept as they are.",
)
data class AccessListRequest(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Optional. If it is sent, it must be the id in the path.")
    val accessListId: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Required. The list's name.")
    val name: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Optional. What the list is for. Left out, it is emptied.")
    val description: String? = null,
)

@Serializable
@JsonSchema.Required("organisationNumbers")
@JsonSchema.Description("Organisations to add to an access list.")
data class NewAccessListMembers(
    @JsonSchema.Description(
        "Up to 100 organisation numbers, each 9 digits. Altinn must know every one of them, or none are added. One " +
            "that is already on the list is skipped.",
    )
    val organisationNumbers: List<String>? = null,
)
