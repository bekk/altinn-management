package no.kartverket.altinnmanagement.restserver.maskinportenschema

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import no.kartverket.altinnmanagement.restserver.ContactPoint
import no.kartverket.altinnmanagement.restserver.Keyword
import no.kartverket.altinnmanagement.restserver.MultilingualText
import no.kartverket.altinnmanagement.restserver.badGateway
import no.kartverket.altinnmanagement.restserver.badRequest
import no.kartverket.altinnmanagement.restserver.clientCertificate
import no.kartverket.altinnmanagement.restserver.conflict
import no.kartverket.altinnmanagement.restserver.example
import no.kartverket.altinnmanagement.restserver.filteringErrorExample
import no.kartverket.altinnmanagement.restserver.idPath
import no.kartverket.altinnmanagement.restserver.internalError
import no.kartverket.altinnmanagement.restserver.invalidIdExample
import no.kartverket.altinnmanagement.restserver.malformedBodyExample
import no.kartverket.altinnmanagement.restserver.missingClientCertificateExample
import no.kartverket.altinnmanagement.restserver.notFound
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.rejectedByAltinnExample
import no.kartverket.altinnmanagement.restserver.requestId
import no.kartverket.altinnmanagement.restserver.validationError
import no.kartverket.altinnmanagement.restserver.withRequiredNotNull

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
    accessListMode = "Disabled",
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

internal val maskinportenSchemaGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get a MaskinportenSchema"
    description = "Gets the resource with this id from Altinn. Only Kartverket's resources of type " +
        "`MaskinportenSchema` are found: any other resource gives `404`, as if it did not exist."

    parameters {
        idPath("kv-matrikkel-brukernavn")
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
        idPath("kv-matrikkel-brukernavn")
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

internal val maskinportenSchemaWithdrawOperation: Operation.Builder.() -> Unit = {
    summary = "Withdraw a MaskinportenSchema"
    description = "Altinn does not let a resource be deleted in production, so this makes it neither `delegable` nor " +
        "`visible` and sets its `status` to `Withdrawn`. The rest is kept as it is. Like `PUT`, it only works on " +
        "Kartverket's MaskinportenSchemas."

    parameters {
        idPath("kv-matrikkel-brukernavn")
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

internal val maskinportenSchemaPolicyPutOperation: Operation.Builder.() -> Unit = {
    summary = "Give a MaskinportenSchema its policy"
    description = "Uploads to Altinn the policy every MaskinportenSchema gets, in place of any policy the resource " +
        "has. The policy lets anyone with the access package `maskinporten-scopes` use the resource's scopes and " +
        "delegate them, after logging in at security level 3 or higher. A resource cannot be delegated until it has " +
        "a policy, so call this after `POST /api/maskinportenschemas`. It only works on Kartverket's " +
        "MaskinportenSchemas, and takes no body."

    parameters {
        idPath("kv-matrikkel-brukernavn")
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
