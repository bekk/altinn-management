package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.Operation
import io.ktor.openapi.jsonSchema
import no.kartverket.altinnmanagement.restserver.badGateway
import no.kartverket.altinnmanagement.restserver.example
import no.kartverket.altinnmanagement.restserver.internalError
import no.kartverket.altinnmanagement.restserver.notFound
import no.kartverket.altinnmanagement.restserver.requestId

private val exampleAccessList = AccessList(
    accessListId = "kv-matrikkel-kunder",
    urn = "urn:altinn:access-list:kv:kv-matrikkel-kunder",
    name = "Matrikkel customers",
    description = "Organisations with an agreement to use the Matrikkel API.",
    createdAt = "2026-11-20T10:15:30.123456+00:00",
    updatedAt = "2026-11-21T08:00:00.000000+00:00",
)

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
