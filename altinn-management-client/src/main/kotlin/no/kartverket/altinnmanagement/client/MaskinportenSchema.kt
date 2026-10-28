package no.kartverket.altinnmanagement.client

public data class MaskinportenSchema(
    public val resourceId: ResourceId,
    public val scopes: List<Scope>,
    public val title: MultilingualText,
    public val description: MultilingualText,
    public val delegationDescription: MultilingualText,
    public val homepage: WebAddress?,
    public val contactPoints: List<ContactPoint>,
    public val keywords: List<Keyword>,
    public val status: ResourceStatus?,
    public val delegable: Boolean,
    public val visible: Boolean,
) {
    init {
        require(scopes.isNotEmpty()) { "Expected a MaskinportenSchema with at least one scope" }
    }
}
