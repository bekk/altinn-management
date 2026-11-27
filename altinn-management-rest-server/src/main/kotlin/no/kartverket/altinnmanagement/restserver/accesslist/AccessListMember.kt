package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable
import no.kartverket.altinnmanagement.client.AccessListMember as ClientAccessListMember

@Serializable
@JsonSchema.Description("A member of one of Kartverket's access lists in Altinn.")
data class AccessListMember(
    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "The organisation's number, 9 digits. Left out when the member is not an organisation, which can only be added " +
            "outside this API.",
    )
    val organisationNumber: String? = null,

    @JsonSchema.Description("When the member was added, as Altinn writes it, like `2026-11-20T10:15:30.123456+00:00`.")
    val addedAt: String,
) {
    companion object {
        fun from(member: ClientAccessListMember) = AccessListMember(member.organisationNumber, member.addedAt)
    }
}
