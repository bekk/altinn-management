package no.kartverket.altinnmanagement.restserver

import io.ktor.http.URLDecodeException
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.queryString
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.ValidationCode
import kotlin.properties.ReadOnlyProperty

internal class InvalidRequestException(val errors: List<FieldError>) : RuntimeException(errors.joinToString("; ") { it.message })

internal class MalformedRequestException(message: String) : RuntimeException(message)

internal class RequestInput {
    private val errors = mutableListOf<FieldError>()

    fun invalid(field: String, code: ValidationCode, message: String) {
        if (errors.none { it.field == field }) errors += FieldError(field, code, message)
    }

    fun requireValid() {
        if (errors.isNotEmpty()) throw InvalidRequestException(errors.toList())
    }

    fun <T : Any> required(value: T?): ReadOnlyProperty<Any?, T> = ReadOnlyProperty { _, _ ->
        requireValid()
        checkNotNull(value)
    }

    fun <T> optional(value: T?): ReadOnlyProperty<Any?, T?> = ReadOnlyProperty { _, _ ->
        requireValid()
        value
    }
}

// Read from the raw query, so we act on the same values as the access check in front of the server: Netty drops
// parameters past the 1024th and reads "=scope=x" as scope, which other parsers do not
internal fun RequestInput.queryParameters(query: String, vararg names: String): Map<String, String> {
    val parameters = if (query.isEmpty()) emptyList() else query.split('&')
    val raw = parameters.map { parameter ->
        val parts = parameter.split('=', limit = 2)
        if (parts.size != 2 || parts[0].isEmpty()) throw MalformedRequestException(onlyThese(names))
        parts[0] to parts[1]
    }
    val values = mutableMapOf<String, String>()
    for ((name, sent) in raw.groupBy({ it.first }, { it.second })) {
        when {
            name !in names -> invalid(name, ValidationCode.NOT_ALLOWED, "$name is not allowed")

            sent.size > 1 -> invalid(name, ValidationCode.NOT_ALLOWED, "$name must be sent once")

            else -> try {
                values[name] = sent.single().decodeURLQueryComponent(plusIsSpace = true)
            } catch (e: URLDecodeException) {
                invalid(name, ValidationCode.INVALID_FORMAT, "$name is not URL-encoded correctly")
            }
        }
    }
    return values
}

private fun onlyThese(names: Array<out String>): String = when (names.size) {
    0 -> "The query must be empty"
    1 -> "The query must contain only the parameter ${names.single()}, sent once"
    else -> "The query must contain only the parameters ${names.dropLast(1).joinToString()} and ${names.last()}, each sent once"
}

internal fun RequestInput.scopeQueryParameter(call: ApplicationCall): ReadOnlyProperty<Any?, String> {
    val scope = queryParameters(call.request.queryString(), "scope")["scope"]
    when {
        scope.isNullOrBlank() -> invalid("scope", ValidationCode.MISSING, "scope is required")
        scope.any(Char::isWhitespace) -> invalid("scope", ValidationCode.INVALID_FORMAT, "scope must not contain whitespace")
    }
    return required(scope)
}

internal fun RequestInput.clientCertificate(call: ApplicationCall, required: Boolean): ReadOnlyProperty<Any?, String?> {
    val certificate = call.request.headers[CLIENT_CERTIFICATE_HEADER]?.takeIf { it.isNotBlank() }
    if (required && certificate == null) {
        invalid(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required")
    }
    return optional(certificate)
}
