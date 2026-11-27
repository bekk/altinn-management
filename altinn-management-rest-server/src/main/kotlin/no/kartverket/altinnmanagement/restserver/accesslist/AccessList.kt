package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.AccessList as ClientAccessList

@Serializable
@JsonSchema.Description(
    "One of Kartverket's access lists in Altinn, with the values Altinn has. A field Altinn has no value for is left " +
        "out. Connected to a resource, the list decides which organisations get access to it.",
)
data class AccessList(
    @JsonSchema.Description("The list's id in Altinn. It cannot be changed.")
    val accessListId: String,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The list as a subject in a policy, like `urn:altinn:access-list:kv:{accessListId}`.")
    val urn: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("The list's name.")
    val name: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("What the list is for. Can be empty.")
    val description: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("When the list was created, as Altinn writes it, like `2026-11-20T10:15:30.123456+00:00`.")
    val createdAt: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("When the list was last changed, as Altinn writes it.")
    val updatedAt: String? = null,
) {
    companion object {
        fun from(list: ClientAccessList) = AccessList(
            accessListId = list.accessListId,
            urn = list.urn,
            name = list.name,
            description = list.description,
            createdAt = list.createdAt,
            updatedAt = list.updatedAt,
        )
    }
}
