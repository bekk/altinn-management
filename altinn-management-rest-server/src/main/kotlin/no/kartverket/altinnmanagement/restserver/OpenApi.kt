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
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.kartverket.altinnmanagement.client.BokmalText
import no.kartverket.altinnmanagement.client.ContactCategory
import no.kartverket.altinnmanagement.client.EmailAddress
import no.kartverket.altinnmanagement.client.EnglishText
import no.kartverket.altinnmanagement.client.KeywordText
import no.kartverket.altinnmanagement.client.Language
import no.kartverket.altinnmanagement.client.NynorskText
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.ResourceStatus
import no.kartverket.altinnmanagement.client.Scope
import no.kartverket.altinnmanagement.client.WebAddress
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.ProblemType
import no.kartverket.altinnmanagement.restserver.models.ValidationCode

private val openApiJson = Json { prettyPrint = true }

private val exampleJson = Json {
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

private val exampleMaskinportenSchema = MaskinportenSchema(
    resourceId = ResourceId.of("kv-matrikkel-brukernavn"),
    scopes = listOf(Scope.of("kartverk:matrikkel:brukernavn")),
    title = MultilingualText(
        en = EnglishText.of("Cadastre API"),
        nb = BokmalText.of("Matrikkel API"),
        nn = NynorskText.of("Matrikkel API"),
    ),
    description = MultilingualText(
        en = EnglishText.of("Machine-to-machine access to the Cadastre."),
        nb = BokmalText.of("Maskinell tilgang til Matrikkelen."),
        nn = NynorskText.of("Maskinell tilgang til Matrikkelen."),
    ),
    delegationDescription = MultilingualText(
        en = EnglishText.of("Allows a system provider to use the Matrikkel API on behalf of your organisation."),
        nb = BokmalText.of("Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din."),
        nn = NynorskText.of("Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di."),
    ),
    homepage = WebAddress.of("https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen"),
    contactPoints = listOf(
        ContactPoint(
            category = ContactCategory.of("Kartverket Kundesenter"),
            email = EmailAddress.of("kundesenter@kartverket.no"),
        ),
    ),
    keywords = listOf(
        Keyword(KeywordText.of("matrikkel"), Language.NB),
        Keyword(KeywordText.of("cadastre"), Language.EN),
    ),
    status = ResourceStatus.COMPLETED,
    delegable = true,
    visible = true,
)

internal val maskinportenSchemaGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get a MaskinportenSchema"
    description = "Gets the resource with this id from Altinn. Only Kartverket's resources of type " +
        "`MaskinportenSchema` are found: any other resource gives `404`, as if it did not exist."

    parameters {
        path("id") {
            required = true
            description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example " +
                "`kv-matrikkel-brukernavn`."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
        }
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The MaskinportenSchema."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<MaskinportenSchema>()
                example("MaskinportenSchema", exampleMaskinportenSchema)
            }
        }

        HttpStatusCode.BadRequest {
            description = "The id is not valid."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "InvalidId",
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(FieldError("id", ValidationCode.INVALID_FORMAT, "id must be 4 or more of a-z, 0-9, _ and -")),
                    ),
                )
            }
        }

        HttpStatusCode.NotFound {
            description = "Kartverket has no MaskinportenSchema with this id in Altinn."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "NotFound",
                    ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, "There is no MaskinportenSchema with this id"),
                )
            }
        }

        HttpStatusCode.BadGateway {
            description = "This API could not get an answer from Altinn. Your request did not cause it."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example("UpstreamError", ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed"))
            }
        }

        HttpStatusCode.InternalServerError {
            description = "An unexpected error in this API."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example("InternalError", ErrorResponse.of(ProblemType.INTERNAL_ERROR, HttpStatusCode.InternalServerError))
            }
        }
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

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
