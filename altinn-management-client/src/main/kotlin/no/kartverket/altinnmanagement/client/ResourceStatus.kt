package no.kartverket.altinnmanagement.client

import kotlinx.serialization.Serializable

@Serializable
public enum class ResourceStatus(internal val altinnName: String?) {
    COMPLETED("Completed"),
    ACTIVE("Active"),
    UNDER_DEVELOPMENT("UnderDevelopment"),
    DEPRECATED("Deprecated"),
    WITHDRAWN("Withdrawn"),
    UNKNOWN(null),
    ;

    internal companion object {
        // Altinn stores the status as free text, so a status a tool there adds must not make the resource unreadable
        fun fromAltinn(name: String): ResourceStatus = entries.find { it.altinnName == name } ?: UNKNOWN
    }
}
