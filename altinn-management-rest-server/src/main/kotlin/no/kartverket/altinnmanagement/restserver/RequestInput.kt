package no.kartverket.altinnmanagement.restserver

import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ValidationCode

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal fun clientCertificate(value: String?, required: Boolean): String? {
    val certificate = value?.takeIf { it.isNotBlank() }
    if (required && certificate == null) {
        throw InvalidRequestException(
            listOf(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
        )
    }
    return certificate
}

internal fun resourceId(name: String, value: String): ResourceId = try {
    ResourceId.of(value)
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException(
        listOf(FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 4 or more of a-z, 0-9, _ and -")),
    )
}
