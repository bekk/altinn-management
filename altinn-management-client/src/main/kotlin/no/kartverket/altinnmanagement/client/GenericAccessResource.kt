package no.kartverket.altinnmanagement.client

public data class GenericAccessResource(
    public val resourceId: ResourceId,
    public val title: MultilingualText?,
    public val description: MultilingualText?,
    public val delegationDescription: MultilingualText?,
    public val homepage: String?,
    public val contactPoints: List<ContactPoint>?,
    public val keywords: List<Keyword>?,
    public val status: String?,
    public val delegable: Boolean?,
    public val visible: Boolean?,
)

public data class NewGenericAccessResource(
    public val resourceId: ResourceId,
    public val title: MultilingualText,
    public val description: MultilingualText,
    public val delegable: Boolean,
    public val delegationDescription: MultilingualText?,
    public val homepage: String? = null,
    public val contactPoints: List<ContactPoint> = emptyList(),
    public val keywords: List<Keyword> = emptyList(),
    public val status: ResourceStatus? = null,
)
