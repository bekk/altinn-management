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

// Altinn takes no more than 100 at a time
private const val MAX_MEMBERS = 100

internal fun newAccessListMembers(body: NewAccessListMembers): List<String> {
    val numbers = body.organisationNumbers ?: throw InvalidRequestException(listOf(missing("organisationNumbers")))
    val errors = buildList {
        if (numbers.size > MAX_MEMBERS) {
            add(FieldError("organisationNumbers", ValidationCode.INVALID_FORMAT, "organisationNumbers must have at most $MAX_MEMBERS entries"))
        }
        numbers.withIndex().filterNot { (_, number) -> isOrganisationNumber(number) }
            .forEach { (index, _) -> add(invalidOrganisationNumber("organisationNumbers[$index]")) }
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return numbers
}

internal fun organisationNumber(name: String, value: String): String =
    value.takeIf(::isOrganisationNumber) ?: throw InvalidRequestException(listOf(invalidOrganisationNumber(name)))

// Altinn checks only that there are 9 digits, not the check digit
private fun isOrganisationNumber(value: String) = value.length == 9 && value.all { it in '0'..'9' }

private fun invalidOrganisationNumber(name: String) = FieldError(name, ValidationCode.INVALID_FORMAT, "$name must be 9 digits")

internal suspend fun ApplicationCall.newAccessListMembersRequest(): NewAccessListMembers = strictBody(NewAccessListMembers.serializer())
