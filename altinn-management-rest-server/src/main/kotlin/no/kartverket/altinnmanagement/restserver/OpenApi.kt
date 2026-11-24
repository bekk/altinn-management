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
import no.kartverket.altinnmanagement.restserver.filtering.CLIENT_CERTIFICATE_HEADER
import no.kartverket.altinnmanagement.restserver.models.AuthenticationLevels
import no.kartverket.altinnmanagement.restserver.models.ContactPoint
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResourcePolicy
import no.kartverket.altinnmanagement.restserver.models.GenericAccessResourceRequest
import no.kartverket.altinnmanagement.restserver.models.Keyword
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.MaskinportenSchemaRequest
import no.kartverket.altinnmanagement.restserver.models.MultilingualText
import no.kartverket.altinnmanagement.restserver.models.NewGenericAccessResource
import no.kartverket.altinnmanagement.restserver.models.NewMaskinportenSchema
import no.kartverket.altinnmanagement.restserver.models.PolicyRule
import no.kartverket.altinnmanagement.restserver.problem.ErrorResponse
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ProblemType
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode

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

        badRequest(
            "`scope` is blank, holds whitespace or is sent more than once, or filtering is on and the " +
                "`X-Forwarded-Client-Cert` header is missing, so this API cannot tell which app is calling.",
            "InvalidScope" to validationError(
                FieldError("scope", ValidationCode.INVALID_FORMAT, "scope must not be blank"),
            ),
            missingClientCertificateExample,
        )

        badGateway(
            "This API could not get an answer from Altinn, or from the service that says which " +
                "resources and scopes the calling app has access to. Your request did not cause it.",
            filteringErrorExample,
        )

        internalError()
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

        badRequest(
            "The body cannot be read or has fields that are missing or invalid, or Altinn rejected the " +
                "resource. When Altinn rejects it, `detail` has Altinn's reasons. The body must be UTF-8 JSON that " +
                "gives each name once.",
            "ValidationError" to validationError(
                FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
            ),
            malformedBodyExample("The body must be one JSON object, with no name given twice"),
            rejectedByAltinnExample("Altinn rejected the resource: InvalidPrefix: skatteetaten:demo"),
        )

        conflict()

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have created the resource.",
        )

        internalError()
    }
}

private val exampleGenericAccessResource = GenericAccessResource(
    resourceId = "kv-grunnbok-innsyn",
    title = MultilingualText(en = "Land Register lookup", nb = "Innsyn i grunnboka", nn = "Innsyn i grunnboka"),
    description = MultilingualText(
        en = "Access to look up registered rights in the Land Register.",
        nb = "Tilgang til å slå opp tinglyste rettigheter i grunnboka.",
        nn = "Tilgang til å slå opp tinglyste rettar i grunnboka.",
    ),
    delegationDescription = MultilingualText(
        en = "Allows a system provider to look up the Land Register on behalf of your organisation.",
        nb = "Gir en systemleverandør rett til å slå opp i grunnboka på vegne av virksomheten din.",
        nn = "Gir ein systemleverandør rett til å slå opp i grunnboka på vegner av verksemda di.",
    ),
    homepage = "https://www.kartverket.no",
    contactPoints = listOf(ContactPoint(category = "Kartverket Kundesenter", email = "kundesenter@kartverket.no")),
    keywords = listOf(Keyword(word = "grunnbok", language = "nb"), Keyword(word = "land register", language = "en")),
    status = "Completed",
    delegable = true,
    visible = true,
)

private val exampleNewGenericAccessResource = NewGenericAccessResource(
    resourceId = exampleGenericAccessResource.resourceId,
    title = exampleGenericAccessResource.title,
    description = exampleGenericAccessResource.description,
    delegable = exampleGenericAccessResource.delegable,
    delegationDescription = exampleGenericAccessResource.delegationDescription,
    homepage = exampleGenericAccessResource.homepage,
    contactPoints = exampleGenericAccessResource.contactPoints,
    keywords = exampleGenericAccessResource.keywords,
    status = exampleGenericAccessResource.status,
)

internal val genericAccessResourceCreateOperation: Operation.Builder.() -> Unit = {
    summary = "Create a GenericAccessResource"
    description = "Creates a resource of type `GenericAccessResource` in Altinn, owned by Kartverket, with access " +
        "lists off. This API checks the fields first, and Altinn checks them again, so a resource that passes here " +
        "can still be rejected. The resource has no policy yet, so no one gets access to it, and it cannot be " +
        "delegated, until it gets one."

    parameters {
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<NewGenericAccessResource>()
            example("NewGenericAccessResource", exampleNewGenericAccessResource)
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
                schema = jsonSchema<GenericAccessResource>()
                example("GenericAccessResource", exampleGenericAccessResource)
            }
        }

        badRequest(
            "The body cannot be read or has fields that are missing or invalid, or Altinn rejected the " +
                "resource. When Altinn rejects it, `detail` has Altinn's reasons. The body must be UTF-8 JSON that " +
                "gives each name once.",
            "ValidationError" to validationError(
                FieldError("delegable", ValidationCode.MISSING, "delegable is required"),
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
            ),
            malformedBodyExample("The body must be one JSON object, with no name given twice"),
            rejectedByAltinnExample("Altinn rejected the resource: Identifier: Invalid Prefix. app_ is only for Altinn Studio apps"),
        )

        conflict()

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have created the resource.",
        )

        internalError()
    }
}

internal val genericAccessResourceGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get a GenericAccessResource"
    description = "Gets the resource with this id from Altinn. Only Kartverket's resources of type " +
        "`GenericAccessResource` are found: any other resource gives `404`, as if it did not exist."

    parameters {
        idPath()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The GenericAccessResource."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<GenericAccessResource>()
                example("GenericAccessResource", exampleGenericAccessResource)
            }
        }

        badRequest("The id is not valid.", invalidIdExample)

        notFound(
            "Kartverket has no GenericAccessResource with this id in Altinn.",
            "There is no GenericAccessResource with this id",
        )

        badGateway("This API could not get an answer from Altinn. Your request did not cause it.")

        internalError()
    }
}

internal val maskinportenSchemaGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get a MaskinportenSchema"
    description = "Gets the resource with this id from Altinn. Only Kartverket's resources of type " +
        "`MaskinportenSchema` are found: any other resource gives `404`, as if it did not exist."

    parameters {
        idPath()
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

        badRequest("The id is not valid.", invalidIdExample)

        notFound("Altinn has no MaskinportenSchema with this id.", "There is no MaskinportenSchema with this id")

        badGateway("This API could not get an answer from Altinn. Your request did not cause it.")

        internalError()
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
        idPath()
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

        badRequest(
            "The id or the body is not valid, the body could not be read, or Altinn rejected the " +
                "resource. Nothing was changed. The body must be UTF-8 JSON that gives each name once. " +
                "`delegationDescription` is checked only when the rest of the body is valid, since this API has to ask " +
                "Altinn whether the resource is `delegable`.",
            "MissingFields" to validationError(
                FieldError("scopes", ValidationCode.MISSING, "scopes is required"),
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
            ),
            malformedBodyExample("The body must be a JSON object with only this API's fields, each of the right type"),
            rejectedByAltinnExample("Altinn rejected the resource: InvalidPrefix: digdir:example"),
        )

        notFound(
            "Kartverket has no MaskinportenSchema with this id in Altinn.",
            "There is no MaskinportenSchema with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been changed.",
        )

        internalError()
    }
}

private val exampleGenericAccessResourcePolicy = GenericAccessResourcePolicy(
    minimumAuthenticationLevel = AuthenticationLevels(endUser = 4, organisation = 3),
    rules = listOf(
        PolicyRule(
            description = "Explicitly granted users may open the service.",
            subjects = listOf(listOf("urn:altinn:accesspackage:eksplisitt")),
            actions = listOf("access"),
        ),
        PolicyRule(
            description = "Explicitly granted users may download the datasets.",
            subjects = listOf(listOf("urn:altinn:accesspackage:eksplisitt")),
            actions = listOf("download"),
            subResources = listOf(mapOf("urn:kv:geonorge" to "dataset")),
        ),
    ),
)

internal val genericAccessResourcePolicyPutOperation: Operation.Builder.() -> Unit = {
    summary = "Set or replace the policy of a GenericAccessResource"
    description = "Uploads the policy in the body to Altinn, in place of any policy the resource has, so it both sets " +
        "and changes a policy. To change one, get it with `GET /api/genericaccessresources/{id}/policy`, change it and " +
        "put all of it back, since rules left out are removed. A resource " +
        "cannot be delegated or used until it has a policy, so call this after `POST /api/genericaccessresources`. " +
        "It only works on Kartverket's GenericAccessResources."

    parameters {
        path("id") {
            required = true
            description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example `kv-grunnbok-innsyn`."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
        }
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<GenericAccessResourcePolicy>().withRequiredNotNull()
            example("Policy", exampleGenericAccessResourcePolicy)
        }
    }

    responses {
        HttpStatusCode.NoContent {
            description = "Altinn has the policy."
            headers { requestId() }
        }

        badRequest(
            "The id or the body is not valid, the body could not be read, or Altinn rejected the policy. " +
                "When Altinn rejects it, `detail` has Altinn's reasons. Nothing was changed. The body must be UTF-8 JSON " +
                "that gives each name once.",
            "InvalidPolicy" to validationError(
                FieldError(
                    "rules[0].subjects[0][0]",
                    ValidationCode.INVALID_FORMAT,
                    "rules[0].subjects[0][0] must be a URN, like urn:altinn:accesspackage:eksplisitt",
                ),
                FieldError("rules[1].actions", ValidationCode.MISSING, "rules[1].actions is required"),
            ),
            malformedBodyExample("The body must be a JSON object with only this API's fields, each of the right type"),
            rejectedByAltinnExample(
                "Altinn rejected the resource: Policy not accepted: Contains rule without reference to registry resource id",
            ),
        )

        notFound(
            "Altinn has no GenericAccessResource of Kartverket's with this id.",
            "There is no GenericAccessResource with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have the policy.",
        )

        internalError()
    }
}

internal val genericAccessResourcePolicyGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get the policy of a GenericAccessResource"
    description = "Gets the resource's policy from Altinn, in the same form as `PUT /api/genericaccessresources/{id}/policy` " +
        "takes it. It only works on Kartverket's GenericAccessResources."

    parameters {
        path("id") {
            required = true
            description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example `kv-grunnbok-innsyn`."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
        }
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The policy."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<GenericAccessResourcePolicy>().withRequiredNotNull()
                example("Policy", exampleGenericAccessResourcePolicy)
            }
        }

        badRequest("The id is not valid.", invalidIdExample)

        notFound(
            "Altinn has no GenericAccessResource of Kartverket's with this id, or the resource has no " +
                "policy yet. A new resource has none until it is given one.",
            "There is no GenericAccessResource with this id, or it has no policy yet",
        )

        badGateway(
            "This API could not get an answer from Altinn, or the policy in Altinn has parts this API " +
                "cannot show, such as a rule with a condition. Your request did not cause it.",
            unsupportedPolicyExample,
        )

        internalError()
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
        idPath()
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

        badRequest(
            "The id is not valid, or Altinn rejected the resource, which it checks as a whole. Nothing " +
                "was changed.",
            invalidIdExample,
            rejectedByAltinnExample("Altinn rejected the resource: Title: Missing title in english en"),
        )

        notFound(
            "Kartverket has no MaskinportenSchema with this id in Altinn.",
            "There is no MaskinportenSchema with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been withdrawn.",
        )

        internalError()
    }
}

private val exampleGenericAccessResourceRequest = GenericAccessResourceRequest(
    title = exampleGenericAccessResource.title,
    description = exampleGenericAccessResource.description,
    delegationDescription = exampleGenericAccessResource.delegationDescription,
    homepage = exampleGenericAccessResource.homepage,
    contactPoints = exampleGenericAccessResource.contactPoints,
    keywords = exampleGenericAccessResource.keywords,
)

internal val genericAccessResourcePutOperation: Operation.Builder.() -> Unit = {
    summary = "Change a GenericAccessResource"
    description = "Replaces the GenericAccessResource with this id in Altinn with the one in the body. Send it whole: " +
        "a field that is left out is removed. What Altinn has that this API does not show, such as the owner and the " +
        "access lists, is kept as it is, and so are `status`, `delegable` and `visible`. Only Kartverket's resources of " +
        "type `GenericAccessResource` can be changed: any other resource gives `404`, as if it did not exist."

    parameters {
        idPath()
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<GenericAccessResourceRequest>().withRequiredNotNull()
            example("GenericAccessResource", exampleGenericAccessResourceRequest)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The GenericAccessResource as this API sent it to Altinn, since Altinn answers a change without a body."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<GenericAccessResource>()
                example("GenericAccessResource", exampleGenericAccessResource)
            }
        }

        badRequest(
            "The id or the body is not valid, the body could not be read, or Altinn rejected the " +
                "resource. When Altinn rejects it, `detail` has Altinn's reasons. Nothing was changed. The body must " +
                "be UTF-8 JSON that gives each name once. `delegationDescription` is checked only when the rest of the " +
                "body is valid, since this API has to ask Altinn whether the resource is `delegable`.",
            invalidIdExample,
            "MissingFields" to validationError(
                FieldError("title.nn", ValidationCode.MISSING, "title.nn is required"),
                FieldError("description", ValidationCode.MISSING, "description is required"),
            ),
            malformedBodyExample("The body must be a JSON object with only this API's fields, each of the right type"),
            rejectedByAltinnExample(
                "Altinn rejected the resource: HasCompetentAuthority.Organization: HasCompetentAuthority needs " +
                    "to be set with valid organization number and orgcode",
            ),
        )

        notFound(
            "Kartverket has no GenericAccessResource with this id in Altinn.",
            "There is no GenericAccessResource with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been changed.",
        )

        internalError()
    }
}

internal val genericAccessResourceWithdrawOperation: Operation.Builder.() -> Unit = {
    summary = "Withdraw a GenericAccessResource"
    description = "Altinn does not let a resource be deleted in production, so this makes it neither `delegable` nor " +
        "`visible` and sets its `status` to `Withdrawn`. The rest is kept as it is. It only works on Kartverket's " +
        "GenericAccessResources."

    parameters {
        idPath()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The GenericAccessResource as this API sent it to Altinn, since Altinn answers a change without a body."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<GenericAccessResource>()
                example(
                    "GenericAccessResource",
                    exampleGenericAccessResource.copy(status = "Withdrawn", delegable = false, visible = false),
                )
            }
        }

        badRequest(
            "The id is not valid, or Altinn rejected the resource, which it checks as a whole. Nothing " +
                "was changed.",
            invalidIdExample,
            rejectedByAltinnExample("Altinn rejected the resource: Title: Missing title in english en"),
        )

        notFound(
            "Kartverket has no GenericAccessResource with this id in Altinn.",
            "There is no GenericAccessResource with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the resource may or may not have been withdrawn.",
        )

        internalError()
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
        idPath()
        requestId()
    }

    responses {
        HttpStatusCode.NoContent {
            description = "Altinn has the policy."
            headers { requestId() }
        }

        badRequest(
            "The id is not valid, or Altinn rejected the policy. When Altinn rejects it, `detail` has " +
                "Altinn's reasons.",
            invalidIdExample,
            rejectedByAltinnExample("Altinn rejected the resource: Unknown resource"),
        )

        notFound(
            "Altinn has no MaskinportenSchema of Kartverket's with this id.",
            "There is no MaskinportenSchema with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have the policy.",
        )

        internalError()
    }
}

internal val genericAccessResourcesListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's GenericAccessResources"
    description = "Gets Kartverket's resources of type `GenericAccessResource` from Altinn. They have no Maskinporten " +
        "scopes, so this API never filters them by scope. When filtering is on, this API only returns the ones the " +
        "calling app has access to: the resource must be in the app's `exact` list or start with one of its " +
        "prefixes. This API reads which app is calling from the `X-Forwarded-Client-Cert` header, which Istio " +
        "usually sets. Altinn's search returns the newest version of each resource that matches, so if a resource's " +
        "type or owner has changed, the list can hold an older version of it."

    parameters {
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "Kartverket's GenericAccessResources, and when filtering is on, only those the calling app " +
                "has access to. The list is empty if there are none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<GenericAccessResource>()))
                example("GenericAccessResources", listOf(exampleGenericAccessResource))
            }
        }

        badRequest(
            "Filtering is on and the `X-Forwarded-Client-Cert` header is missing, so this API cannot tell " +
                "which app is calling.",
            missingClientCertificateExample,
        )

        badGateway(
            "This API could not get an answer from Altinn, or from the service that says which " +
                "resources the calling app has access to. Your request did not cause it.",
            filteringErrorExample,
        )

        internalError()
    }
}

private fun Parameters.Builder.idPath() {
    path("id") {
        required = true
        description = "The resource's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example " +
            "`kv-matrikkel-brukernavn`."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
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

internal fun Responses.Builder.badRequest(description: String, vararg examples: Pair<String, ErrorResponse>) =
    problem(HttpStatusCode.BadRequest, description, examples.toList())

internal fun Responses.Builder.notFound(description: String, detail: String) = problem(
    HttpStatusCode.NotFound,
    description,
    listOf("NotFound" to ErrorResponse.of(ProblemType.NOT_FOUND, HttpStatusCode.NotFound, detail)),
)

internal fun Responses.Builder.conflict() = problem(
    HttpStatusCode.Conflict,
    "Altinn already has a resource with this id. It may be of another type or owner, so a GET of the id can still give `404`.",
    listOf("Conflict" to ErrorResponse.of(ProblemType.CONFLICT, HttpStatusCode.Conflict, "Altinn already has a resource with this id")),
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
