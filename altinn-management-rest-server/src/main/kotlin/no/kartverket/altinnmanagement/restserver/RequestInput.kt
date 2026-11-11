package no.kartverket.altinnmanagement.restserver

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import no.kartverket.altinnmanagement.client.MaskinportenSchemaContent
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.ResourceStatus
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchemaRequest
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.NewMaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import no.kartverket.altinnmanagement.client.ContactPoint as ClientContactPoint
import no.kartverket.altinnmanagement.client.Keyword as ClientKeyword
import no.kartverket.altinnmanagement.client.MultilingualText as ClientMultilingualText
import no.kartverket.altinnmanagement.client.NewMaskinportenSchema as ClientNewMaskinportenSchema

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

internal fun scope(values: List<String>?): String? {
    if (values == null) return null
    val scope = values.singleOrNull() ?: invalidScope("scope must be sent once")
    if (scope.isBlank()) invalidScope("scope must not be blank")
    if (scope.any(Char::isWhitespace)) invalidScope("scope must not contain whitespace")
    return scope
}

private fun invalidScope(message: String): Nothing =
    throw InvalidRequestException(listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, message)))

internal fun resourceId(name: String, value: String): ResourceId = try {
    ResourceId.of(value)
} catch (e: IllegalArgumentException) {
    throw InvalidRequestException(
        listOf(FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 4 or more of a-z, 0-9, _ and -")),
    )
}

// Checks what Altinn would refuse, so that a caller gets every error at once, by this API's field names
internal fun maskinportenSchemaContent(id: ResourceId, request: MaskinportenSchemaRequest): MaskinportenSchemaContent {
    val errors = buildList {
        if (request.resourceId != null && request.resourceId != id.value) {
            add(FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be the id in the path"))
        }
        if (request.scopes.isNullOrEmpty()) add(missing("scopes"))
        request.scopes?.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                add(FieldError("scopes[$index]", ValidationCode.INVALID_FORMAT, "scopes[$index] must not be blank"))
            }
        }
        addAll(textErrors("title", request.title))
        addAll(textErrors("description", request.description))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return MaskinportenSchemaContent(
        scopes = request.scopes!!,
        title = request.title!!.toClient(),
        description = request.description!!.toClient(),
        delegationDescription = request.delegationDescription?.toClient(),
        homepage = request.homepage,
        contactPoints = request.contactPoints.orEmpty().map { it.toClient() },
        keywords = request.keywords.orEmpty().map { it.toClient() },
    )
}

private fun textErrors(field: String, text: MultilingualText?): List<FieldError> {
    if (text == null) return listOf(missing(field))
    return listOf("en" to text.en, "nb" to text.nb, "nn" to text.nn)
        .filter { (_, value) -> value.isNullOrBlank() }
        .map { (language, _) -> missing("$field.$language") }
}

private fun missing(field: String) = FieldError(field, ValidationCode.MISSING, "$field is required")

internal suspend fun ApplicationCall.maskinportenSchemaRequest(): MaskinportenSchemaRequest =
    strictBody(MaskinportenSchemaRequest.serializer())

internal suspend fun ApplicationCall.newMaskinportenSchemaRequest(): NewMaskinportenSchema = strictBody(NewMaskinportenSchema.serializer())

private suspend fun <T> ApplicationCall.strictBody(serializer: KSerializer<T>): T {
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

// Checks every field before it fails, so the caller gets all the errors at once
internal fun newMaskinportenSchema(body: NewMaskinportenSchema): ClientNewMaskinportenSchema {
    val errors = mutableListOf<FieldError>()
    val resourceId = when {
        body.resourceId.isNullOrBlank() -> null.also { errors += missing("resourceId") }

        else -> try {
            ResourceId.of(body.resourceId)
        } catch (e: IllegalArgumentException) {
            errors += FieldError("resourceId", ValidationCode.INVALID_FORMAT, "resourceId must be 4 or more of a-z, 0-9, _ and -")
            null
        }
    }
    if (body.scopes.isNullOrEmpty()) {
        errors += FieldError("scopes", ValidationCode.MISSING, "scopes must have at least one scope")
    }
    body.scopes.orEmpty().forEachIndexed { index, scope ->
        val field = "scopes[$index]"
        when {
            scope.isBlank() -> errors += FieldError(field, ValidationCode.MISSING, "$field must not be blank")
            scope.any(Char::isWhitespace) -> errors += FieldError(field, ValidationCode.INVALID_FORMAT, "$field must not contain whitespace")
        }
    }
    val title = multilingualText("title", body.title, errors)
    val description = multilingualText("description", body.description, errors)
    val delegationDescription = multilingualText("delegationDescription", body.delegationDescription, errors)
    val status = body.status?.let { value -> ResourceStatus.entries.find { it.value == value } }
    if (body.status != null && status == null) {
        val values = ResourceStatus.entries.joinToString { it.value }
        errors += FieldError("status", ValidationCode.INVALID_FORMAT, "status must be one of $values")
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)

    return ClientNewMaskinportenSchema(
        resourceId = resourceId!!,
        scopes = body.scopes!!,
        title = title!!,
        description = description!!,
        delegationDescription = delegationDescription!!,
        homepage = body.homepage?.takeIf { it.isNotBlank() },
        contactPoints = body.contactPoints.orEmpty().map {
            ClientContactPoint(category = it.category, email = it.email, telephone = it.telephone, contactPage = it.contactPage)
        },
        keywords = body.keywords.orEmpty().map { ClientKeyword(word = it.word, language = it.language) },
        status = status,
    )
}

private fun multilingualText(field: String, text: MultilingualText?, errors: MutableList<FieldError>): ClientMultilingualText? {
    if (text == null) {
        errors += missing(field)
        return null
    }
    val missing = listOf("en" to text.en, "nb" to text.nb, "nn" to text.nn).filter { (_, value) -> value.isNullOrBlank() }
    missing.forEach { (language, _) -> errors += missing("$field.$language") }
    return if (missing.isEmpty()) ClientMultilingualText(en = text.en, nb = text.nb, nn = text.nn) else null
}
