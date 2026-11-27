package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

public data class AccessList(
    public val accessListId: String,
    public val urn: String?,
    public val name: String?,
    public val description: String?,
    public val createdAt: String?,
    public val updatedAt: String?,
)

@Serializable
internal data class AltinnAccessList(
    val identifier: String,
    val urn: String? = null,
    val name: String? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val resourceConnections: List<AltinnResourceConnection>? = null,
) {
    fun toAccessList(): AccessList = AccessList(identifier, urn, name, description, createdAt, updatedAt)
}

// Altinn takes a left out description as an empty one, so one is always sent
@Serializable
internal data class AltinnAccessListContent(val name: String, val description: String)

@Serializable
internal data class AltinnResourceConnection(val resourceIdentifier: String)

@Serializable
internal data class AltinnAccessListPage(
    val links: AltinnPageLinks = AltinnPageLinks(),
    val data: List<AltinnAccessList>,
)

@Serializable
internal data class AltinnPageLinks(val next: String? = null)

/** A member of an access list. [organisationNumber] is null when the member is not an organisation. */
public data class AccessListMember(
    public val organisationNumber: String?,
    public val addedAt: String,
)

@Serializable
internal data class AltinnAccessListMember(val since: String, val identifiers: JsonObject? = null) {
    fun toAccessListMember(): AccessListMember =
        AccessListMember((identifiers?.get(ORGANISATION_NUMBER_URN) as? JsonPrimitive)?.content, since)
}

@Serializable
internal data class AltinnAccessListMemberPage(
    val links: AltinnPageLinks = AltinnPageLinks(),
    val data: List<AltinnAccessListMember>,
)

@Serializable
internal data class AltinnAccessListMembers(val data: List<String>)

internal const val ORGANISATION_NUMBER_URN = "urn:altinn:organization:identifier-no"
