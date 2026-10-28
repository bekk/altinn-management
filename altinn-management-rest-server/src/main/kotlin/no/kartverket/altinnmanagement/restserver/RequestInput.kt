package no.kartverket.altinnmanagement.restserver

import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ValidationCode

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal fun resourceId(name: String, value: String): ResourceId = try {
    ResourceId.of(value)
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException(
        listOf(FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 4 or more of a-z, 0-9, _ and -")),
    )
}
