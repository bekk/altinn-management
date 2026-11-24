package no.kartverket.altinnmanagement.restserver

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.ResourceStatus
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal class MalformedRequestException(message: String) : RuntimeException(message)

internal fun clientCertificate(value: String?, required: Boolean): String? {
    val certificate = value?.takeIf { it.isNotBlank() }
    if (required && certificate == null) {
        throw InvalidRequestException(
            listOf(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")),
        )
    }
    return certificate
}

internal fun resourceId(name: String, value: String): ResourceId =
    resourceIdOrNull(value) ?: throw InvalidRequestException(listOf(invalidResourceId(name)))

internal fun resourceIdOrNull(value: String): ResourceId? = try {
    ResourceId.of(value)
} catch (e: IllegalArgumentException) {
    null
}

private fun invalidResourceId(name: String) = FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 4 or more of a-z, 0-9, _ and -")

// Altinn requires it only for a resource that can be delegated, and takes a resource without delegable as one
internal fun checkDelegationDescription(delegable: Boolean?, delegationDescription: MultilingualText?) {
    if (delegable == false) return
    val errors = textErrors("delegationDescription", delegationDescription)
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
}

internal fun pathIdErrors(id: ResourceId, resourceId: String?): List<FieldError> =
    if (resourceId == null || resourceId == id.value) {
        emptyList()
    } else {
        listOf(FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be the id in the path"))
    }

internal fun textErrors(field: String, text: MultilingualText?): List<FieldError> {
    if (text == null) return listOf(missing(field))
    return listOf("en" to text.en, "nb" to text.nb, "nn" to text.nn)
        .filter { (_, value) -> value.isNullOrBlank() }
        .map { (language, _) -> missing("$field.$language") }
}

internal fun missing(field: String) = FieldError(field, ValidationCode.MISSING, "$field is required")

internal suspend fun <T> ApplicationCall.strictBody(serializer: KSerializer<T>): T {
    val body = strictJson(jsonBody())
    return try {
        apiJson.decodeFromString(serializer, body)
    } catch (e: SerializationException) {
        throw MalformedRequestException("The body must be a JSON object with only this API's fields, each of the right type")
    }
}

private val strictJsonMapper = JsonMapper.builder()
    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .build()

// The filtering service reads the body before this app does. kotlinx keeps the last of two equal names, so a body that
// gives a name twice could have it allow other scopes than the ones sent to Altinn. Jackson's reason is not given,
// since it can quote the body
private fun strictJson(body: String): String {
    try {
        strictJsonMapper.readTree(body)
    } catch (e: JacksonException) {
        throw MalformedRequestException("The body must be one JSON object, with no name given twice")
    }
    return body
}

private suspend fun ApplicationCall.jsonBody(): String {
    requireJsonContentType()
    return try {
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(receive<ByteArray>())).toString()
    } catch (e: CharacterCodingException) {
        throw MalformedRequestException("The body must be UTF-8")
    }
}

private fun ApplicationCall.requireJsonContentType() {
    val contentType = try {
        request.contentType()
    } catch (e: BadContentTypeFormatException) {
        null
    }
    if (contentType?.withoutParameters() != ContentType.Application.Json) {
        throw MalformedRequestException("The body must be JSON, sent with Content-Type application/json")
    }
}

internal fun newResourceIdErrors(value: String?, resourceId: ResourceId?): List<FieldError> = when {
    value.isNullOrBlank() -> listOf(missing("resourceId"))

    resourceId == null -> listOf(invalidResourceId("resourceId"))

    resourceId.isFixedRoute -> listOf(
        FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must not be $resourceId, which Altinn uses as a route"),
    )

    else -> emptyList()
}

internal fun resourceStatus(value: String?): ResourceStatus? = value?.let { ResourceStatus.entries.find { it.value == value } }

internal fun statusErrors(value: String?, status: ResourceStatus?): List<FieldError> {
    if (value == null || status != null) return emptyList()
    val values = ResourceStatus.entries.joinToString { it.value }
    return listOf(FieldError("status", ValidationCode.INVALID_FORMAT, "status must be one of $values"))
}
