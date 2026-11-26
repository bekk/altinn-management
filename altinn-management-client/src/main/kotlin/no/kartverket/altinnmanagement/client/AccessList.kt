package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

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
) {
    fun toAccessList(): AccessList = AccessList(identifier, urn, name, description, createdAt, updatedAt)
}
