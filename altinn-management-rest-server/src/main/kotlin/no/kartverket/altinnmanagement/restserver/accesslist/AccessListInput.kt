package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.server.application.ApplicationCall
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.InvalidRequestException
import no.kartverket.altinnmanagement.restserver.invalidResourceId
import no.kartverket.altinnmanagement.restserver.missing
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.resourceId
import no.kartverket.altinnmanagement.restserver.resourceIdOrNull
import no.kartverket.altinnmanagement.restserver.strictBody

/** A list as it is sent to Altinn, after the checks. */
internal data class AccessListFields(val id: String, val name: String, val description: String?)

/** The resource in the query, or null when there is none. */
internal fun resource(values: List<String>?): ResourceId? {
    if (values == null) return null
    val value = values.singleOrNull()
        ?: throw InvalidRequestException(listOf(FieldError("resource", ValidationCode.INVALID_FORMAT, "resource must be sent once")))
    return resourceId("resource", value)
}

/** A list's id follows the same rule as a resource's id. */
internal fun accessListId(name: String, value: String): String = resourceId(name, value).value

internal fun newAccessList(body: NewAccessList): AccessListFields {
    val errors = buildList {
        when {
            body.accessListId.isNullOrBlank() -> add(missing("accessListId"))
            resourceIdOrNull(body.accessListId) == null -> add(invalidResourceId("accessListId"))
        }
        if (body.name.isNullOrBlank()) add(missing("name"))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return AccessListFields(body.accessListId!!, body.name!!, body.description)
}

internal fun accessListContent(id: String, body: AccessListRequest): AccessListFields {
    val errors = buildList {
        if (body.accessListId != null && body.accessListId != id) {
            add(FieldError("accessListId", ValidationCode.INVALID_FORMAT, "accessListId must be the id in the path"))
        }
        if (body.name.isNullOrBlank()) add(missing("name"))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return AccessListFields(id, body.name!!, body.description)
}

internal suspend fun ApplicationCall.newAccessListRequest(): NewAccessList = strictBody(NewAccessList.serializer())

internal suspend fun ApplicationCall.accessListRequest(): AccessListRequest = strictBody(AccessListRequest.serializer())
