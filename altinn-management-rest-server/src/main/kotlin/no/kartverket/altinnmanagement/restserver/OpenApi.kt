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
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.ErrorResponse
import no.kartverket.altinnmanagement.restserver.models.FieldError
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchemaRequest
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.NewMaskinportenSchema
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
    resourceId = "kv-matrikkel-brukernavn",
    scopes = listOf("kartverk:matrikkel:brukernavn"),
    title = MultilingualText(en = "Cadastre API", nb = "Matrikkel API", nn = "Matrikkel API"),
    description = MultilingualText(
        en = "Machine-to-machine access to the Cadastre.",
        nb = "Maskinell tilgang til Matrikkelen.",
        nn = "Maskinell tilgang til Matrikkelen.",
    ),
    delegationDescription = MultilingualText(
        en = "Allows a system provider to use the Matrikkel API on behalf of your organisation.",
        nb = "Gir en systemleverandør rett til å bruke Matrikkel API på vegne av virksomheten din.",
        nn = "Gir ein systemleverandør rett til å bruke Matrikkel API på vegner av verksemda di.",
    ),
    homepage = "https://kartverket.github.io/api-dokumentasjon/docs/matrikkelen",
    contactPoints = listOf(ContactPoint(category = "Kartverket Kundesenter", email = "kundesenter@kartverket.no")),
    keywords = listOf(Keyword(word = "matrikkel", language = "nb"), Keyword(word = "cadastre", language = "en")),
    status = "Completed",
    delegable = true,
    visible = true,
)

internal val maskinportenSchemasListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's MaskinportenSchemas"
    description = "Gets Kartverket's resources of type `MaskinportenSchema` from Altinn, or with `scope`, only the ones " +
        "that give that Maskinporten scope. When filtering is on, this API only returns the ones the calling app has " +
        "access to: the resource, and every one of its scopes, must be in the app's `exact` list or start with one of " +
        "its prefixes. This holds with `scope` too, so a resource that also gives a scope the app may not see is left " +
        "out. This API reads which app is calling from the `X-Forwarded-Client-Cert` header, which Istio " +
        "usually sets. Altinn's search returns the newest version of each resource that matches, so if a resource's " +
        "type or owner has changed, the list can hold an older version of it. Get a resource by its id to be sure you " +
        "have its current version."

    parameters {
        query("scope") {
            required = false
            description = "Only the resources that give this Maskinporten scope, for example " +
                "`kartverk:matrikkel:brukernavn`. It must match exactly, case included. Send one scope, at most once."
            schema = JsonSchema(type = JsonType.STRING)
        }
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "Kartverket's MaskinportenSchemas, only those that give `scope` when it is sent, and when " +
                "filtering is on, only those the calling app has access to. The list is empty if there are none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<MaskinportenSchema>()))
                example("MaskinportenSchemas", listOf(exampleMaskinportenSchema))
            }
        }

        HttpStatusCode.BadRequest {
            description = "`scope` is blank, holds whitespace or is sent more than once, or filtering is on and the " +
                "`X-Forwarded-Client-Cert` header is missing, so this API cannot tell which app is calling."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "InvalidScope",
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not be blank")),
                    ),
                )
                example(
                    "MissingClientCertificate",
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(
                            FieldError(CLIENT_CERTIFICATE_HEADER, ValidationCode.MISSING, "$CLIENT_CERTIFICATE_HEADER is required"),
                        ),
                    ),
                )
            }
        }

        HttpStatusCode.BadGateway {
            description = "This API could not get an answer from Altinn, or from the service that says which " +
                "resources and scopes the calling app has access to. Your request did not cause it."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example("UpstreamError", ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to Altinn failed"))
                example(
                    "FilteringError",
                    ErrorResponse.of(ProblemType.UPSTREAM_ERROR, HttpStatusCode.BadGateway, "The call to the filtering service failed"),
                )
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

private val exampleNewMaskinportenSchema = NewMaskinportenSchema(
    resourceId = exampleMaskinportenSchema.resourceId,
    scopes = exampleMaskinportenSchema.scopes,
    title = exampleMaskinportenSchema.title,
    description = exampleMaskinportenSchema.description,
    delegationDescription = exampleMaskinportenSchema.delegationDescription,
    homepage = exampleMaskinportenSchema.homepage,
    contactPoints = exampleMaskinportenSchema.contactPoints,
    keywords = exampleMaskinportenSchema.keywords,
    status = exampleMaskinportenSchema.status,
)

internal val maskinportenSchemaCreateOperation: Operation.Builder.() -> Unit = {
    summary = "Create a MaskinportenSchema"
    description = "Creates a resource of type `MaskinportenSchema` in Altinn, owned by Kartverket. This API checks " +
        "the fields first, and Altinn checks them again, so a resource that passes here can still be rejected. The " +
        "resource has no policy yet, so it cannot be delegated until it gets one with " +
        "`PUT /api/maskinportenschemas/{id}/policy`."

    parameters {
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<NewMaskinportenSchema>()
            example("NewMaskinportenSchema", exampleNewMaskinportenSchema)
        }
    }

    responses {
        HttpStatusCode.Created {
            description = "Altinn created the resource. The body has the values this API sent to Altinn, as a GET " +
                "would answer them."
            headers {
                requestId()
                header(HttpHeaders.Location) {
                    description = "Where to get the new resource from this API."
                    required = true
                    schema = JsonSchema(type = JsonType.STRING)
                }
            }
            ContentType.Application.Json {
                schema = jsonSchema<MaskinportenSchema>()
                example("MaskinportenSchema", exampleMaskinportenSchema)
            }
        }

        HttpStatusCode.BadRequest {
            description = "The body cannot be read or has fields that are missing or invalid, or Altinn rejected the " +
                "resource. When Altinn rejects it, `detail` has Altinn's reasons. The body must be UTF-8 JSON that " +
                "gives each name once."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "ValidationError",
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(
                            FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                            FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                        ),
                    ),
                )
                example(
                    "MalformedBody",
                    ErrorResponse.of(
                        ProblemType.MALFORMED_REQUEST,
                        HttpStatusCode.BadRequest,
                        "The body must be one JSON object, with no name given twice",
                    ),
                )
                example(
                    "RejectedByAltinn",
                    ErrorResponse.of(
                        ProblemType.UPSTREAM_REJECTED,
                        HttpStatusCode.BadRequest,
                        "Altinn rejected the resource: InvalidPrefix: skatteetaten:demo",
                    ),
                )
            }
        }

        HttpStatusCode.Conflict {
            description = "Altinn already has a resource with this id. It may be of another type or owner, so a GET " +
                "of the id can still give `404`."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "Conflict",
                    ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, "Altinn already has a resource with this id"),
                )
            }
        }

        HttpStatusCode.BadGateway {
            description = "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have created the resource."
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

internal val maskinportenSchemaGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get a MaskinportenSchema"
    description = "Gets the resource with this id from Altinn. Only resources of type `MaskinportenSchema` are " +
        "found: any other resource gives `404`, as if it did not exist."

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
            description = "Altinn has no MaskinportenSchema with this id."
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

private val exampleMaskinportenSchemaRequest = MaskinportenSchemaRequest(
    scopes = exampleMaskinportenSchema.scopes,
    title = exampleMaskinportenSchema.title,
    description = exampleMaskinportenSchema.description,
    delegationDescription = exampleMaskinportenSchema.delegationDescription,
    homepage = exampleMaskinportenSchema.homepage,
    contactPoints = exampleMaskinportenSchema.contactPoints,
    keywords = exampleMaskinportenSchema.keywords,
)

internal val maskinportenSchemaPutOperation: Operation.Builder.() -> Unit = {
    summary = "Change a MaskinportenSchema"
    description = "Replaces the MaskinportenSchema with this id in Altinn with the one in the body. Send it whole: a " +
        "field that is left out is removed. What Altinn has that this API does not show, such as the owner, is kept " +
        "as it is, and so are `status`, `delegable` and `visible`. Only Kartverket's resources of type " +
        "`MaskinportenSchema` can be changed: any other resource gives `404`, as if it did not exist."

    parameters {
        path("id") {
            required = true
            description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example " +
                "`kv-matrikkel-brukernavn`."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
        }
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<MaskinportenSchemaRequest>().withRequiredNotNull()
            example("MaskinportenSchema", exampleMaskinportenSchemaRequest)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The MaskinportenSchema as this API sent it to Altinn, since Altinn answers a change without a body."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<MaskinportenSchema>()
                example("MaskinportenSchema", exampleMaskinportenSchema)
            }
        }

        HttpStatusCode.BadRequest {
            description = "The id or the body is not valid, the body could not be read, or Altinn rejected the " +
                "resource. Nothing was changed. The body must be UTF-8 JSON that gives each name once."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "MissingFields",
                    ErrorResponse.of(
                        ProblemType.VALIDATION_ERROR,
                        HttpStatusCode.BadRequest,
                        errors = listOf(
                            FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                            FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                        ),
                    ),
                )
                example(
                    "MalformedBody",
                    ErrorResponse.of(
                        ProblemType.MALFORMED_REQUEST,
                        HttpStatusCode.BadRequest,
                        "The body must be a JSON object with only this API's fields, each of the right type",
                    ),
                )
                example(
                    "RejectedByAltinn",
                    ErrorResponse.of(
                        ProblemType.UPSTREAM_REJECTED,
                        HttpStatusCode.BadRequest,
                        "Altinn rejected the resource: InvalidPrefix: digdir:example",
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
            description = "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been changed."
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

// A required field can be null in the request model only so that every missing field is reported at once. Ktor lets an
// object field be null whenever its Kotlin type is nullable, and no annotation changes that.
private fun JsonSchema.withRequiredNotNull(): JsonSchema = copy(
    properties = properties?.mapValues { (name, property) ->
        if (name in required.orEmpty() && property is ReferenceOr.Value) ReferenceOr.Value(property.value.notNull()) else property
    },
)

private fun JsonSchema.notNull(): JsonSchema {
    val anyOf = type as? JsonSchema.SchemaType.AnyOf ?: return this
    val types = anyOf.types - JsonType.NULL
    return copy(type = types.singleOrNull() ?: JsonSchema.SchemaType.AnyOf(types))
}

internal val maskinportenSchemaWithdrawOperation: Operation.Builder.() -> Unit = {
    summary = "Withdraw a MaskinportenSchema"
    description = "Altinn does not let a resource be deleted in production, so this makes it neither `delegable` nor " +
        "`visible` and sets its `status` to `Withdrawn`. The rest is kept as it is. Like `PUT`, it only works on " +
        "Kartverket's MaskinportenSchemas."

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
            description = "The MaskinportenSchema as this API sent it to Altinn, since Altinn answers a change without a body."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<MaskinportenSchema>()
                example(
                    "MaskinportenSchema",
                    exampleMaskinportenSchema.copy(status = "Withdrawn", delegable = false, visible = false),
                )
            }
        }

        HttpStatusCode.BadRequest {
            description = "The id is not valid, or Altinn rejected the resource, which it checks as a whole. Nothing " +
                "was changed."
            headers { requestId() }
            ContentType.Application.ProblemJson {
                schema = jsonSchema<ErrorResponse>()
                example(
                    "RejectedByAltinn",
                    ErrorResponse.of(
                        ProblemType.UPSTREAM_REJECTED,
                        HttpStatusCode.BadRequest,
                        "Altinn rejected the resource: Title: Missing title in english en",
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
            description = "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been withdrawn."
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

internal val maskinportenSchemaPolicyPutOperation: Operation.Builder.() -> Unit = {
    summary = "Give a MaskinportenSchema its policy"
    description = "Uploads to Altinn the policy every MaskinportenSchema gets, in place of any policy the resource " +
        "has. The policy lets anyone with the access package `maskinporten-scopes` use the resource's scopes and " +
        "delegate them, after logging in at security level 3 or higher. A resource cannot be delegated until it has " +
        "a policy, so call this after `POST /api/maskinportenschemas`. It only works on Kartverket's " +
        "MaskinportenSchemas, and takes no body."

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
        HttpStatusCode.NoContent {
            description = "Altinn has the policy."
            headers { requestId() }
        }

        HttpStatusCode.BadRequest {
            description = "The id is not valid, or Altinn rejected the policy. When Altinn rejects it, `detail` has " +
                "Altinn's reasons."
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
                example(
                    "RejectedByAltinn",
                    ErrorResponse.of(
                        ProblemType.UPSTREAM_REJECTED,
                        HttpStatusCode.BadRequest,
                        "Altinn rejected the resource: Unknown resource",
                    ),
                )
            }
        }

        HttpStatusCode.NotFound {
            description = "Altinn has no MaskinportenSchema of Kartverket's with this id."
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
            description = "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have the policy."
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

private fun Parameters.Builder.clientCertificate() {
    header(CLIENT_CERTIFICATE_HEADER) {
        description = "Says which app is calling. Istio usually sets it on every call, so the app does not. This " +
            "API requires it when filtering is on."
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

private inline fun <reified T : Any> MediaType.Builder.example(name: String, value: T) =
    example(name, ExampleObject(value = GenericElement(exampleJson.encodeToJsonElement(value))))
