package no.kartverket.altinnmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ValidationCode {
    MISSING,
    INVALID_FORMAT,
    NOT_ALLOWED,
}
