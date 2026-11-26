package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import no.kartverket.altinnmanagement.restserver.badGateway
import no.kartverket.altinnmanagement.restserver.badRequest
import no.kartverket.altinnmanagement.restserver.clientCertificate
import no.kartverket.altinnmanagement.restserver.example
import no.kartverket.altinnmanagement.restserver.filteringErrorExample
import no.kartverket.altinnmanagement.restserver.internalError
import no.kartverket.altinnmanagement.restserver.missingClientCertificateExample
import no.kartverket.altinnmanagement.restserver.notFound
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.requestId
import no.kartverket.altinnmanagement.restserver.validationError

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
