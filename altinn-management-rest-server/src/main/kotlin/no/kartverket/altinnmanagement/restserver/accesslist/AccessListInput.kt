package no.kartverket.altinnmanagement.restserver.accesslist

import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.InvalidRequestException
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.resourceId

/** The resource in the query, or null when there is none. */
internal fun resource(values: List<String>?): ResourceId? {
    if (values == null) return null
    val value = values.singleOrNull()
        ?: throw InvalidRequestException(listOf(FieldError("resource", ValidationCode.INVALID_FORMAT, "resource must be sent once")))
    return resourceId("resource", value)
}
