package no.kartverket.altinnmanagement.client

/** The statuses in use on resources in Altinn. Altinn itself takes any text. */
public enum class ResourceStatus(public val value: String) {
    COMPLETED("Completed"),
    ACTIVE("Active"),
    UNDER_DEVELOPMENT("UnderDevelopment"),
    DEPRECATED("Deprecated"),
    WITHDRAWN("Withdrawn"),
}

public enum class AccessListMode(public val value: String) {
    ENABLED("Enabled"),
    DISABLED("Disabled"),
}

public data class MultilingualText(
    public val en: String?,
    public val nb: String?,
    public val nn: String?,
)

public data class ContactPoint(
    public val category: String?,
    public val email: String?,
    public val telephone: String?,
    public val contactPage: String?,
)

public data class Keyword(
    public val word: String?,
    public val language: String?,
)
