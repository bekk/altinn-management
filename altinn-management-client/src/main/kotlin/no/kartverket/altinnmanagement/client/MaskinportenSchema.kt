package no.kartverket.altinnmanagement.client

public data class MaskinportenSchema(
    public val resourceId: ResourceId,
    public val scopes: List<String>?,
    public val title: MultilingualText?,
    public val description: MultilingualText?,
    public val delegationDescription: MultilingualText?,
    public val homepage: String?,
    public val contactPoints: List<ContactPoint>?,
    public val keywords: List<Keyword>?,
    public val status: String?,
    public val delegable: Boolean?,
    public val visible: Boolean?,
    public val accessListMode: String?,
)

public data class MaskinportenSchemaContent(
    public val scopes: List<String>,
    public val title: MultilingualText,
    public val description: MultilingualText,
    public val delegationDescription: MultilingualText?,
    public val homepage: String?,
    public val contactPoints: List<ContactPoint>,
    public val keywords: List<Keyword>,
)

public data class NewMaskinportenSchema(
    public val resourceId: ResourceId,
    public val scopes: List<String>,
    public val title: MultilingualText,
    public val description: MultilingualText,
    public val delegationDescription: MultilingualText,
    public val homepage: String? = null,
    public val contactPoints: List<ContactPoint> = emptyList(),
    public val keywords: List<Keyword> = emptyList(),
    public val status: ResourceStatus? = null,
)
