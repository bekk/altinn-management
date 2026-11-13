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
