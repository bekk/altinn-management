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
import io.ktor.openapi.Operation
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
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.ErrorCode
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.ValidationCode

private val openApiJson = Json { prettyPrint = true }

private val exampleJson = Json {
    encodeDefaults = true
    explicitNulls = false
}

private val apiInfo = OpenApiInfo(
    title = "Altinn Management REST API",
    version = "v0",
    description = """
        Manages Kartverket's resources in Altinn's Resource Registry.

        A call only reaches this API after the platform has checked that the calling app may make it. When a list
        holds resources, it only shows the ones the calling app has access to.
    """.trimIndent(),
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())

private val exampleMaskinportenSchema = MaskinportenSchema(
    resourceId = "kv-matrikkel-brukernavn",
    scopes = listOf("kartverk:matrikkel:brukernavn"),
    title = mapOf("en" to "Matrikkel API", "nb" to "Matrikkel-API", "nn" to "Matrikkel-API"),
    description = mapOf("en" to "Access to the Matrikkel API", "nb" to "Tilgang til Matrikkel-API-et", "nn" to "Tilgang til Matrikkel-API-et"),
    delegationDescription = mapOf("en" to "May use the Matrikkel API", "nb" to "Kan bruke Matrikkel-API-et", "nn" to "Kan bruke Matrikkel-API-et"),
    homepage = "https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen",
    contactPoints = listOf(ContactPoint(category = "Kartverket Kundesenter", email = "kundesenter@kartverket.no")),
    keywords = listOf(Keyword(word = "matrikkel", language = "nb")),
    status = "Completed",
    delegable = true,
    visible = true,
)

private fun Parameters.Builder.clientCertificate() {
    header(CLIENT_CERTIFICATE_HEADER) {
        required = true
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not."
        schema = JsonSchema(type = JsonType.STRING)
    }
}

private fun Parameters.Builder.requestId() {
    header(HttpHeaders.XRequestId) {
        description = "An id to find this call by in this API's logs. Up to 128 ASCII letters, digits, `-`, `_`, " +
            "`.` and `:`. Without it, or with any other value, this API makes a new one. Every response has the id " +
            "in its `X-Request-Id` header."
        schema = JsonSchema(type = JsonType.STRING)
        example = GenericElement("3f2b8c1e-7d4a-4f0e-9b6a-2c5d8e1f4a7b")
    }
}

private fun Headers.Builder.requestId() = header(HttpHeaders.XRequestId) {
    description = "The id of this call in this API's logs: the `X-Request-Id` you sent, or a new one."
    required = true
    schema = JsonSchema(type = JsonType.STRING)
}

private fun validationError(field: String, code: ValidationCode, message: String) = ErrorResponse.of(
    HttpStatusCode.BadRequest,
    "Validation failed",
    ErrorCode.VALIDATION_ERROR,
    listOf(FieldError(field, code, message)),
)

internal val maskinportenSchemasByScopeOperation: Operation.Builder.() -> Unit = {
    summary = "List the MaskinportenSchema resources for a scope"
    description = """
        Returns Kartverket's MaskinportenSchema resources in Altinn that point to `scope`, as Altinn has them now,
        withdrawn ones included: check `status`, `delegable` and `visible`. A resource must point to exactly `scope`,
        case included: `kartverk:a` does not find a resource for `kartverk:a.read`.

        The list only holds the resources the calling app has access to: those in its `exact` list, and those whose id
        starts with one of its prefixes. This API reads which app is calling from the `X-Forwarded-Client-Cert` header,
        which Istio usually sets.
    """.trimIndent()

    parameters {
        query("scope") {
            required = true
            description = "The Maskinporten scope, for example `kartverk:matrikkel:brukernavn`. It must be the only " +
                "query parameter, sent once."
            schema = JsonSchema(type = JsonType.STRING)
        }
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The resources. The list is empty if there are none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<MaskinportenSchema>()))
                example("Resources", listOf(exampleMaskinportenSchema))
            }
        }

        HttpStatusCode.BadRequest {
            description = "The query is not exactly one `scope`, or `scope` is blank, contains whitespace or is not " +
                "URL-encoded correctly, or the `X-Forwarded-Client-Cert` header is missing."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example("MissingScope", validationError("scope", ValidationCode.MISSING, "scope is required"))
                example(
                    "MissingClientCertificate",
                    validationError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required"),
                )
            }
        }

        HttpStatusCode.BadGateway {
            description = "This API could not get a usable answer from Altinn, or from the service that says which " +
                "resources the calling app has access to. Your request did not cause it."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example("UpstreamError", ErrorResponse.of(HttpStatusCode.BadGateway, "The call to Altinn failed", ErrorCode.UPSTREAM_ERROR))
                example(
                    "FilteringError",
                    ErrorResponse.of(HttpStatusCode.BadGateway, "The call to the filtering service failed", ErrorCode.UPSTREAM_ERROR),
                )
            }
        }
    }
}

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
