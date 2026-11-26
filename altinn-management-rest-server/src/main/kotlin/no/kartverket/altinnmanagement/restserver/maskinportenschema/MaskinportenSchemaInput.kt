package no.kartverket.altinnmanagement.restserver.maskinportenschema

import io.ktor.server.application.ApplicationCall
import no.kartverket.altinnmanagement.client.MaskinportenSchemaContent
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.restserver.InvalidRequestException
import no.kartverket.altinnmanagement.restserver.checkDelegationDescription
import no.kartverket.altinnmanagement.restserver.missing
import no.kartverket.altinnmanagement.restserver.newResourceIdErrors
import no.kartverket.altinnmanagement.restserver.pathIdErrors
import no.kartverket.altinnmanagement.restserver.problem.FieldError
import no.kartverket.altinnmanagement.restserver.problem.ValidationCode
import no.kartverket.altinnmanagement.restserver.resourceIdOrNull
import no.kartverket.altinnmanagement.restserver.resourceStatus
import no.kartverket.altinnmanagement.restserver.statusErrors
import no.kartverket.altinnmanagement.restserver.strictBody
import no.kartverket.altinnmanagement.restserver.textErrors
import no.kartverket.altinnmanagement.client.NewMaskinportenSchema as ClientNewMaskinportenSchema

internal fun scope(values: List<String>?): String? {
    if (values == null) return null
    val scope = values.singleOrNull() ?: invalidScope("scope must be sent once")
    if (scope.isBlank()) invalidScope("scope must not be blank")
    if (scope.any(Char::isWhitespace)) invalidScope("scope must not contain whitespace")
    return scope
}

private fun invalidScope(message: String): Nothing =
    throw InvalidRequestException(listOf(FieldError("scope", ValidationCode.INVALID_FORMAT, message)))

// Checks what Altinn would refuse, by this API's field names. What does not depend on the resource is checked before
// Altinn is asked, so that a body with errors gives 400 and not 404 or 502
internal fun maskinportenSchemaContent(
    id: ResourceId,
    request: MaskinportenSchemaRequest,
): (delegable: Boolean?) -> MaskinportenSchemaContent {
    val errors = buildList {
        addAll(pathIdErrors("resourceId", id.value, request.resourceId))
        addAll(scopeErrors(request.scopes))
        addAll(textErrors("title", request.title))
        addAll(textErrors("description", request.description))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return { delegable ->
        checkDelegationDescription(delegable, request.delegationDescription)
        MaskinportenSchemaContent(
            scopes = request.scopes!!,
            title = request.title!!.toClient(),
            description = request.description!!.toClient(),
            delegationDescription = request.delegationDescription?.toClient(),
            homepage = request.homepage,
            contactPoints = request.contactPoints.orEmpty().map { it.toClient() },
            keywords = request.keywords.orEmpty().map { it.toClient() },
        )
    }
}

// Altinn takes a scope with whitespace, but no OAuth scope can hold any (RFC 6749, section 3.3)
private fun scopeErrors(scopes: List<String>?): List<FieldError> {
    if (scopes.isNullOrEmpty()) return listOf(missing("scopes"))
    return scopes.withIndex().mapNotNull { (index, scope) ->
        val field = "scopes[$index]"
        when {
            scope.isBlank() -> FieldError(field, ValidationCode.INVALID_FORMAT, "$field must not be blank")
            scope.any(Char::isWhitespace) -> FieldError(field, ValidationCode.INVALID_FORMAT, "$field must not contain whitespace")
            else -> null
        }
    }
}

internal suspend fun ApplicationCall.maskinportenSchemaRequest(): MaskinportenSchemaRequest =
    strictBody(MaskinportenSchemaRequest.serializer())

internal suspend fun ApplicationCall.newMaskinportenSchemaRequest(): NewMaskinportenSchema = strictBody(NewMaskinportenSchema.serializer())

// Checks every field before it fails, so the caller gets all the errors at once
internal fun newMaskinportenSchema(body: NewMaskinportenSchema): ClientNewMaskinportenSchema {
    val resourceId = body.resourceId?.let(::resourceIdOrNull)
    val status = resourceStatus(body.status)
    val errors = buildList {
        addAll(newResourceIdErrors(body.resourceId, resourceId))
        addAll(scopeErrors(body.scopes))
        addAll(textErrors("title", body.title))
        addAll(textErrors("description", body.description))
        addAll(textErrors("delegationDescription", body.delegationDescription))
        addAll(statusErrors(body.status, status))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)

    return ClientNewMaskinportenSchema(
        resourceId = resourceId!!,
        scopes = body.scopes!!,
        title = body.title!!.toClient(),
        description = body.description!!.toClient(),
        delegationDescription = body.delegationDescription!!.toClient(),
        homepage = body.homepage,
        contactPoints = body.contactPoints.orEmpty().map { it.toClient() },
        keywords = body.keywords.orEmpty().map { it.toClient() },
        status = status,
    )
}
