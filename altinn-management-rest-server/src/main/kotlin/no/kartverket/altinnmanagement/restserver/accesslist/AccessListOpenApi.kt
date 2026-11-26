package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.Parameters
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import no.kartverket.altinnmanagement.restserver.badGateway
import no.kartverket.altinnmanagement.restserver.badRequest
import no.kartverket.altinnmanagement.restserver.clientCertificate
import no.kartverket.altinnmanagement.restserver.conflict
import no.kartverket.altinnmanagement.restserver.example
import no.kartverket.altinnmanagement.restserver.filteringErrorExample
import no.kartverket.altinnmanagement.restserver.internalError
import no.kartverket.altinnmanagement.restserver.invalidIdExample
import no.kartverket.altinnmanagement.restserver.malformedBodyExample
import no.kartverket.altinnmanagement.restserver.missingClientCertificateExample
import no.kartverket.altinnmanagement.restserver.notFound
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.requestId
import no.kartverket.altinnmanagement.restserver.validationError
import no.kartverket.altinnmanagement.restserver.withRequiredNotNull

private val exampleAccessList = AccessList(
    accessListId = "kv-matrikkel-kunder",
    urn = "urn:altinn:access-list:kv:kv-matrikkel-kunder",
    name = "Matrikkel customers",
    description = "Organisations with an agreement to use the Matrikkel API.",
    createdAt = "2026-11-20T10:15:30.123456+00:00",
    updatedAt = "2026-11-21T08:00:00.000000+00:00",
)

internal val accessListsListOperation: Operation.Builder.() -> Unit = {
    summary = "List Kartverket's access lists"
    description = "Gets Kartverket's access lists from Altinn, from every page Altinn gives, or with `resource`, only " +
        "the lists connected to that resource. When filtering is on, this API only returns the lists whose id starts " +
        "with one of the calling app's prefixes for resources. An `exact` resource id gives no access to a list. This " +
        "API reads which app is calling from the `X-Forwarded-Client-Cert` header, which Istio usually sets."

    parameters {
        query("resource") {
            required = false
            description = "Only the lists connected to this resource, for example `kv-geonorge`. It is a resource's " +
                "id: 4 or more of `a-z`, `0-9`, `_` and `-`. Send one resource, at most once."
            schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
        }
        clientCertificate()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "Kartverket's access lists, only those connected to `resource` when it is sent, and when " +
                "filtering is on, only those the calling app has access to. The list is empty if there are none."
            headers { requestId() }
            ContentType.Application.Json {
                schema = JsonSchema(type = JsonType.ARRAY, items = ReferenceOr.Value(jsonSchema<AccessList>()))
                example("AccessLists", listOf(exampleAccessList))
            }
        }

        badRequest(
            "`resource` is not a resource's id or is sent more than once, or filtering is on and the " +
                "`X-Forwarded-Client-Cert` header is missing, so this API cannot tell which app is calling.",
            "InvalidResource" to validationError(
                FieldError("resource", ValidationCode.INVALID_FORMAT, "resource must be 4 or more of a-z, 0-9, _ and -"),
            ),
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

internal val accessListGetOperation: Operation.Builder.() -> Unit = {
    summary = "Get an access list"
    description = "Gets Kartverket's access list with this id from Altinn."

    parameters {
        path("id") {
            required = true
            description = "The list's id, for example `kv-matrikkel-kunder`. Altinn matches it exactly, case included."
            schema = JsonSchema(type = JsonType.STRING)
        }
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "The access list."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<AccessList>()
                example("AccessList", exampleAccessList)
            }
        }

        notFound("Kartverket has no access list with this id in Altinn.", "There is no access list with this id")

        badGateway("This API could not get an answer from Altinn. Your request did not cause it.")

        internalError()
    }
}

private val exampleNewAccessList = NewAccessList(
    accessListId = "kv-matrikkel-kunder",
    name = "Matrikkel customers",
    description = "Organisations with an agreement to use the Matrikkel API.",
)

private val exampleAccessListRequest = AccessListRequest(
    name = "Matrikkel customers",
    description = "Organisations with an agreement to use the Matrikkel API.",
)

private fun Parameters.Builder.accessListIdPath() {
    path("id") {
        required = true
        description = "The list's id: 4 or more of `a-z`, `0-9`, `_` and `-`, for example `kv-matrikkel-kunder`."
        schema = JsonSchema(type = JsonType.STRING, pattern = "^[a-z0-9_-]{4,}$")
    }
}

internal val accessListCreateOperation: Operation.Builder.() -> Unit = {
    summary = "Create an access list"
    description = "Creates an access list in Altinn, owned by Kartverket, with no members and connected to no " +
        "resource. This API checks the id and the name first, and Altinn checks the list again."

    parameters {
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<NewAccessList>().withRequiredNotNull()
            example("NewAccessList", exampleNewAccessList)
        }
    }

    responses {
        HttpStatusCode.Created {
            description = "Altinn created the list. The body is the list as Altinn has it."
            headers {
                requestId()
                header(HttpHeaders.Location) {
                    description = "Where to get the new list from this API."
                    required = true
                    schema = JsonSchema(type = JsonType.STRING)
                }
            }
            ContentType.Application.Json {
                schema = jsonSchema<AccessList>()
                example("AccessList", exampleAccessList)
            }
        }

        badRequest(
            "The body cannot be read or has fields that are missing or invalid, or Altinn rejected the list. When " +
                "Altinn rejects it, `detail` has Altinn's reasons. The body must be UTF-8 JSON that gives each name once.",
            "ValidationError" to validationError(
                FieldError("accessListId", ValidationCode.INVALID_FORMAT, "accessListId must be 4 or more of a-z, 0-9, _ and -"),
                FieldError("name", ValidationCode.MISSING, "name is required"),
            ),
            malformedBodyExample("The body must be one JSON object, with no name given twice"),
        )

        conflict(
            "Kartverket already has an access list with this id in Altinn.",
            "Altinn already has an access list with this id",
        )

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it. Altinn may or may not have created the list.",
        )

        internalError()
    }
}

internal val accessListPutOperation: Operation.Builder.() -> Unit = {
    summary = "Change an access list"
    description = "Replaces the name and description of Kartverket's access list with this id in Altinn. Send both: " +
        "a description that is left out is emptied. The list's members and the resources it is connected to are kept " +
        "as they are. A list that does not exist gives `404`, and is not created."

    parameters {
        accessListIdPath()
        requestId()
    }

    requestBody {
        required = true
        ContentType.Application.Json {
            schema = jsonSchema<AccessListRequest>().withRequiredNotNull()
            example("AccessList", exampleAccessListRequest)
        }
    }

    responses {
        HttpStatusCode.OK {
            description = "The list as Altinn has it after the change."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<AccessList>()
                example("AccessList", exampleAccessList)
            }
        }

        badRequest(
            "The id or the body is not valid, the body could not be read, or Altinn rejected the list. When Altinn " +
                "rejects it, `detail` has Altinn's reasons. Nothing was changed. The body must be UTF-8 JSON that " +
                "gives each name once.",
            invalidIdExample,
            "MissingName" to validationError(FieldError("name", ValidationCode.MISSING, "name is required")),
            malformedBodyExample("The body must be a JSON object with only this API's fields, each of the right type"),
        )

        notFound("Kartverket has no access list with this id in Altinn.", "There is no access list with this id")

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the list may or may not have been changed.",
        )

        internalError()
    }
}

internal val accessListDeleteOperation: Operation.Builder.() -> Unit = {
    summary = "Delete an access list"
    description = "Deletes Kartverket's access list with this id from Altinn."

    parameters {
        accessListIdPath()
        requestId()
    }

    responses {
        HttpStatusCode.OK {
            description = "Altinn deleted the list. The body is the list as it was."
            headers { requestId() }
            ContentType.Application.Json {
                schema = jsonSchema<AccessList>()
                example("AccessList", exampleAccessList)
            }
        }

        badRequest("The id is not valid. Nothing was deleted.", invalidIdExample)

        notFound("Kartverket has no access list with this id in Altinn.", "There is no access list with this id")

        badGateway(
            "This API could not get an answer from Altinn, or a token from Maskinporten. Your request " +
                "did not cause it, and the list may or may not have been deleted.",
        )

        internalError()
    }
}
