package no.kartverket.altinnmanagement.restserver.genericaccessresource

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
import no.kartverket.altinnmanagement.restserver.unsupportedPolicyExample
import no.kartverket.altinnmanagement.restserver.validationError
import no.kartverket.altinnmanagement.restserver.withRequiredNotNull

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
        idPath("kv-grunnbok-innsyn")
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
        idPath("kv-grunnbok-innsyn")
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
        idPath("kv-grunnbok-innsyn")
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
        idPath("kv-grunnbok-innsyn")
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
        idPath("kv-grunnbok-innsyn")
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
