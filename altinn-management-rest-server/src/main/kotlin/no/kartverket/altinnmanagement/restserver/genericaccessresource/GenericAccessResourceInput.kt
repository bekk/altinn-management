package no.kartverket.altinnmanagement.restserver.genericaccessresource

import io.ktor.server.application.ApplicationCall
import no.kartverket.altinnmanagement.client.GenericAccessResourceContent
import no.kartverket.altinnmanagement.client.PolicyEffect
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
import no.kartverket.altinnmanagement.client.AuthenticationLevels as ClientAuthenticationLevels
import no.kartverket.altinnmanagement.client.GenericAccessResourcePolicy as ClientGenericAccessResourcePolicy
import no.kartverket.altinnmanagement.client.NewGenericAccessResource as ClientNewGenericAccessResource
import no.kartverket.altinnmanagement.client.PolicyRule as ClientPolicyRule

// Checks what Altinn would refuse, by this API's field names. What does not depend on the resource is checked before
// Altinn is asked, so that a body with errors gives 400 and not 404 or 502
internal fun genericAccessResourceContent(
    id: ResourceId,
    request: GenericAccessResourceRequest,
): (delegable: Boolean?) -> GenericAccessResourceContent {
    val errors = buildList {
        addAll(pathIdErrors("resourceId", id.value, request.resourceId))
        addAll(textErrors("title", request.title))
        addAll(textErrors("description", request.description))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)
    return { delegable ->
        checkDelegationDescription(delegable, request.delegationDescription)
        GenericAccessResourceContent(
            title = request.title!!.toClient(),
            description = request.description!!.toClient(),
            delegationDescription = request.delegationDescription?.toClient(),
            homepage = request.homepage,
            contactPoints = request.contactPoints.orEmpty().map { it.toClient() },
            keywords = request.keywords.orEmpty().map { it.toClient() },
        )
    }
}

internal suspend fun ApplicationCall.newGenericAccessResourceRequest(): NewGenericAccessResource =
    strictBody(NewGenericAccessResource.serializer())

internal suspend fun ApplicationCall.genericAccessResourceRequest(): GenericAccessResourceRequest =
    strictBody(GenericAccessResourceRequest.serializer())

internal suspend fun ApplicationCall.genericAccessResourcePolicyRequest(): GenericAccessResourcePolicy =
    strictBody(GenericAccessResourcePolicy.serializer())

internal fun newGenericAccessResource(body: NewGenericAccessResource): ClientNewGenericAccessResource {
    val resourceId = body.resourceId?.let(::resourceIdOrNull)
    val status = resourceStatus(body.status)
    val errors = buildList {
        addAll(newResourceIdErrors(body.resourceId, resourceId))
        addAll(textErrors("title", body.title))
        addAll(textErrors("description", body.description))
        if (body.delegable == null) add(missing("delegable"))
        // Altinn requires it only for a resource that can be delegated
        if (body.delegable == true) addAll(textErrors("delegationDescription", body.delegationDescription))
        addAll(statusErrors(body.status, status))
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)

    return ClientNewGenericAccessResource(
        resourceId = resourceId!!,
        title = body.title!!.toClient(),
        description = body.description!!.toClient(),
        delegable = body.delegable!!,
        delegationDescription = body.delegationDescription?.toClient(),
        homepage = body.homepage,
        contactPoints = body.contactPoints.orEmpty().map { it.toClient() },
        keywords = body.keywords.orEmpty().map { it.toClient() },
        status = status,
    )
}

// A rule without subjects or actions would match everyone and everything, and Altinn cannot list its rules
internal fun genericAccessResourcePolicy(body: GenericAccessResourcePolicy): ClientGenericAccessResourcePolicy {
    val errors = buildList {
        if (body.rules.isNullOrEmpty()) add(missing("rules"))
        body.rules.orEmpty().forEachIndexed { index, rule ->
            val field = "rules[$index]"
            if (rule.subjects.isNullOrEmpty()) add(missing("$field.subjects"))
            rule.subjects.orEmpty().forEachIndexed { g, group ->
                if (group.isEmpty()) add(missing("$field.subjects[$g]"))
                group.forEachIndexed { i, subject ->
                    val subjectField = "$field.subjects[$g][$i]"
                    subjectError(subject)?.let { add(FieldError(subjectField, ValidationCode.INVALID_FORMAT, "$subjectField $it")) }
                }
            }
            if (rule.actions.isNullOrEmpty()) add(missing("$field.actions"))
            rule.actions.orEmpty().withIndex().filter { (_, action) -> action.isBlank() }.forEach { (i, _) ->
                add(FieldError("$field.actions[$i]", ValidationCode.INVALID_FORMAT, "$field.actions[$i] must not be blank"))
            }
            rule.subResources.orEmpty().forEachIndexed { s, subResource ->
                val subField = "$field.subResources[$s]"
                if (subResource.isEmpty()) add(missing(subField))
                if (subResource.any { (attributeId, value) -> attributeId.isBlank() || value.isBlank() }) {
                    add(FieldError(subField, ValidationCode.INVALID_FORMAT, "$subField must not have a blank attribute id or value"))
                }
                // The rule names its resource with this attribute already, so a second one would match nothing
                if ("urn:altinn:resource" in subResource) {
                    add(FieldError(subField, ValidationCode.INVALID_FORMAT, "$subField must not have urn:altinn:resource, which the rule already has"))
                }
            }
            if (rule.effect != null && effect(rule.effect) == null) {
                val values = PolicyEffect.entries.joinToString { it.value }
                add(FieldError("$field.effect", ValidationCode.INVALID_FORMAT, "$field.effect must be one of $values"))
            }
        }
    }
    if (errors.isNotEmpty()) throw InvalidRequestException(errors)

    val levels = body.minimumAuthenticationLevel
    return ClientGenericAccessResourcePolicy(
        minimumAuthenticationLevel = ClientAuthenticationLevels(levels?.endUser, levels?.organisation, levels?.systemUser),
        rules = body.rules!!.map {
            ClientPolicyRule(
                subjects = it.subjects!!,
                actions = it.actions!!,
                subResources = it.subResources.orEmpty(),
                description = it.description,
                effect = it.effect?.let(::effect) ?: PolicyEffect.PERMIT,
            )
        },
    )
}

private fun effect(value: String) = PolicyEffect.entries.find { it.value == value }

// The client writes an access list's owner and list together as the value, so a subject without both would match no one
private fun subjectError(subject: String): String? = when {
    subject.substringBeforeLast(':', "").isBlank() || subject.substringAfterLast(':', "").isBlank() ->
        "must be a URN, like urn:altinn:accesspackage:eksplisitt"

    subject.startsWith("urn:altinn:access-list", ignoreCase = true) && !ACCESS_LIST.matches(subject) ->
        "must be an access list as urn:altinn:access-list:{owner}:{list}, like urn:altinn:access-list:kv:testliste"

    else -> null
}

private val ACCESS_LIST = Regex("""urn:altinn:access-list:[^:\s]+:[^:\s]+""")
