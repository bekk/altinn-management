package no.kartverket.altinnmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.Headers
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.MediaType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Parameters
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.Responses
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode

private val openApiJson = Json { prettyPrint = true }

internal val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

private val apiInfo = OpenApiInfo(
    title = "Altinn Management REST API",
    version = "v0",
    description = "Manages Kartverket's resources in Altinn's Resource Registry.",
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())

// A required field can be null in the request model only so that every missing field is reported at once. Ktor lets an
// object field be null whenever its Kotlin type is nullable, and no annotation changes that.
internal fun JsonSchema.withRequiredNotNull(): JsonSchema = copy(
    properties = properties?.mapValues { (name, property) ->
        if (name in required.orEmpty() && property is ReferenceOr.Value) ReferenceOr.Value(property.value.notNull()) else property
    },
)

private fun JsonSchema.notNull(): JsonSchema {
    val anyOf = type as? JsonSchema.SchemaType.AnyOf ?: return this
    val types = anyOf.types - JsonType.NULL
    return copy(type = types.singleOrNull() ?: JsonSchema.SchemaType.AnyOf(types))
}

internal fun Parameters.Builder.idPath(example: String) {
    path("id") {
        required = true
        description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example `$example`."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
    }
}

internal fun Parameters.Builder.clientCertificate() {
    header(CLIENT_CERTIFICATE_HEADER) {
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not. This " +
            "API requires it when filtering is on."
        schema = JsonSchema(type = JsonType.STRING)
    }
}

internal fun Parameters.Builder.requestId() {
    header(HttpHeaders.XRequestId) {
        description = "An id to find this call by in this API's logs. Up to 128 ASCII letters, digits, `-`, `_`, " +
            "`.` and `:`. Without it, or with any other value, this API makes a new one. Every response has the id " +
            "in its `X-Request-Id` header."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement("3f2b8c1e-7d4a-4f0e-9b6a-2c5d8e1f4a7b")
    }
}

internal fun Headers.Builder.requestId() = header(HttpHeaders.XRequestId) {
    description = "The id of this call in this API's logs: the `X-Request-Id` you sent, or a new one."
    required = true
    schema = JsonSchema(type = JsonType.STRING)
}

internal inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))

internal fun Responses.Builder.badRequest(description: String, vararg examples: Pair<String, ErrorResponse>) =
    problem(HttpStatusCode.BadRequest, description, examples.toList())

internal fun Responses.Builder.notFound(description: String, detail: String) = problem(
    HttpStatusCode.NotFound,
    description,
    listOf("NotFound" to ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, detail)),
)

internal fun Responses.Builder.conflict(
    description: String =
        "Altinn already has a resource with this id. It may be of another type or owner, so a GET of the id can still give `404`.",
    detail: String = "Altinn already has a resource with this id",
) = problem(
    HttpStatusCode.Conflict,
    description,
    listOf("Conflict" to ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, detail)),
)

internal fun Responses.Builder.badGateway(description: String, vararg examples: Pair<String, ErrorResponse>) = problem(
    HttpStatusCode.BadGateway,
    description,
    listOf("UpstreamError" to ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed")) +
        examples,
)

internal fun Responses.Builder.internalError() = problem(
    HttpStatusCode.InternalServerError,
    "An unexpected error in this API.",
    listOf("InternalError" to ErrorResponse.of(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError)),
)

private fun Responses.Builder.problem(status: HttpStatusCode, description: String, examples: List<Pair<String, ErrorResponse>>) {
    status {
        this.description = description
        headers { requestId() }
        ContentType.Application.ProblemJson {
            schema = jsonSchema<ErrorResponse>()
            examples.forEach { (name, value) -> example(name, value) }
        }
    }
}

internal fun validationError(vararg errors: FieldError) =
    ErrorResponse.of(ProblemType.VALIDATION_ERROR, HttpStatusCode.BadRequest, errors = errors.toList())

internal fun malformedBodyExample(detail: String) =
    "MalformedBody" to ErrorResponse.of(ProblemType.MALFORMED_REQUEST, HttpStatusCode.BadRequest, detail)

internal fun rejectedByAltinnExample(detail: String) =
    "RejectedByAltinn" to ErrorResponse.of(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.BadRequest, detail)

internal val invalidIdExample =
    "InvalidId" to validationError(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -"))

internal val missingClientCertificateExample = "MissingClientCertificate" to
    validationError(FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required"))

internal val filteringErrorExample =
    "FilteringError" to ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed")

internal val unsupportedPolicyExample = "UnsupportedPolicy" to ErrorResponse.of(ProblemType.UNSUPPORTED_POLICY, HttpStatusCode.BadGateway)
