package no.kartverket.altinnmanagement.client

public data class GenericAccessResourcePolicy(
    public val minimumAuthenticationLevel: AuthenticationLevels = AuthenticationLevels(),
    public val rules: List<PolicyRule>,
) {
    init {
        require(rules.isNotEmpty()) { "A policy must have at least one rule" }
    }
}

public data class AuthenticationLevels(
    public val endUser: Int? = null,
    public val organisation: Int? = null,
    public val systemUser: Int? = null,
)

/**
 * Lets [subjects] do [actions] on the resource, or with [PolicyEffect.DENY] stops them. Each list in [subjects] is a
 * group of subjects, such as `urn:altinn:accesspackage:eksplisitt` or `urn:altinn:rolecode:dagl`, and a user matches
 * the rule by matching every subject in any one group. Each map in [subResources] is one part of the resource the rule
 * covers, as attribute ids and values. Left empty, the rule covers the whole resource.
 */
public data class PolicyRule(
    public val subjects: List<List<String>>,
    public val actions: List<String>,
    public val subResources: List<Map<String, String>> = emptyList(),
    public val description: String? = null,
    public val effect: PolicyEffect = PolicyEffect.PERMIT,
) {
    init {
        require(subjects.isNotEmpty()) { "A rule must have at least one group of subjects" }
        require(subjects.none { it.isEmpty() }) { "A group of subjects must not be empty" }
        require(subjects.flatten().all { it.substringBeforeLast(':', "").isNotBlank() && it.substringAfterLast(':', "").isNotBlank() }) {
            "A subject must be a URN, like urn:altinn:accesspackage:eksplisitt"
        }
        require(subjects.flatten().none { it.startsWith(ACCESS_LIST_PREFIX, ignoreCase = true) && !ACCESS_LIST.matches(it) }) {
            "An access list must be given as urn:altinn:access-list:{owner}:{list}, like urn:altinn:access-list:kv:testliste"
        }
        require(actions.isNotEmpty()) { "A rule must have at least one action" }
        require(actions.none { it.isBlank() }) { "An action must not be blank" }
        require(subResources.none { it.isEmpty() }) { "A sub-resource must have at least one attribute" }
        require(subResources.none { it.any { (attributeId, value) -> attributeId.isBlank() || value.isBlank() } }) {
            "A sub-resource must not have a blank attribute id or value"
        }
    }
}

/** A DENY rule wins over every PERMIT rule that matches the same request. */
public enum class PolicyEffect(public val value: String) {
    PERMIT("Permit"),
    DENY("Deny"),
}

// Written as Altinn Studio writes a policy (PolicyConverter.cs), though Studio has no Deny rules or groups of subjects. A
// group shares one AllOf, since Altinn's PDP and registry keep only one of several subject AnyOfs (tested in TT02)
internal fun genericAccessResourcePolicy(id: ResourceId, policy: GenericAccessResourcePolicy): String {
    val rules = policy.rules.withIndex().joinToString("\n") { (index, rule) -> rule(id, index + 1, rule) }
    val levels = policy.minimumAuthenticationLevel
    val obligations = listOfNotNull(
        levels.endUser?.let { obligation(1, "urn:altinn:minimum-authenticationlevel", it) },
        levels.organisation?.let { obligation(2, "urn:altinn:minimum-authenticationlevel-org", it) },
        levels.systemUser?.let { obligation(3, "urn:altinn:minimum-authenticationlevel-systemuser", it) },
    )
    return buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine(
            """<xacml:Policy xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" PolicyId="urn:altinn:policyid:1" """ +
                """Version="1.0" RuleCombiningAlgId="urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-overrides">""",
        )
        appendLine("<xacml:Target />")
        appendLine(rules)
        if (obligations.isNotEmpty()) {
            appendLine("<xacml:ObligationExpressions>")
            obligations.forEach(::appendLine)
            appendLine("</xacml:ObligationExpressions>")
        }
        append("</xacml:Policy>")
    }
}

private fun rule(id: ResourceId, number: Int, rule: PolicyRule): String {
    val subjects = rule.subjects.map { group ->
        group.map { subject ->
            val (attributeId, value) = subjectAttribute(subject)
            match(STRING_EQUAL_IGNORE_CASE, value, attributeId, SUBJECT_CATEGORY)
        }
    }
    val resource = match(STRING_EQUAL, id.value, "urn:altinn:resource", RESOURCE_CATEGORY)
    val resources = rule.subResources.ifEmpty { listOf(emptyMap()) }.map { subResource ->
        listOf(resource) + subResource.map { (attributeId, value) -> match(STRING_EQUAL, value, attributeId, RESOURCE_CATEGORY) }
    }
    val actions = rule.actions.map { listOf(match(STRING_EQUAL_IGNORE_CASE, it, ACTION_ID, ACTION_CATEGORY)) }
    return listOf(
        """<xacml:Rule RuleId="urn:altinn:resource:${id.value}:ruleid:$number" Effect="${rule.effect.value}">""",
        "<xacml:Description>${xml(rule.description.orEmpty())}</xacml:Description>",
        "<xacml:Target>",
        anyOf(subjects),
        anyOf(resources),
        anyOf(actions),
        "</xacml:Target>",
        "</xacml:Rule>",
    ).joinToString("\n")
}

private fun subjectAttribute(subject: String): Pair<String, String> {
    val split = if (subject.startsWith(ACCESS_LIST_PREFIX, ignoreCase = true)) {
        subject.substringBeforeLast(':').lastIndexOf(':')
    } else {
        subject.lastIndexOf(':')
    }
    return subject.take(split) to subject.drop(split + 1)
}

private fun anyOf(allOfs: List<List<String>>): String =
    "<xacml:AnyOf>\n" + allOfs.joinToString("\n") { "<xacml:AllOf>\n${it.joinToString("\n")}\n</xacml:AllOf>" } + "\n</xacml:AnyOf>"

private fun match(matchId: String, value: String, attributeId: String, category: String): String = listOf(
    """<xacml:Match MatchId="$matchId">""",
    """<xacml:AttributeValue DataType="$STRING">${xml(value)}</xacml:AttributeValue>""",
    """<xacml:AttributeDesignator AttributeId="${xml(attributeId)}" Category="$category" DataType="$STRING" MustBePresent="false" />""",
    "</xacml:Match>",
).joinToString("\n")

private fun obligation(number: Int, category: String, level: Int): String = """
    |<xacml:ObligationExpression ObligationId="urn:altinn:obligation:authenticationLevel$number" FulfillOn="Permit">
    |<xacml:AttributeAssignmentExpression AttributeId="urn:altinn:obligation$number-assignment$number" Category="$category">
    |<xacml:AttributeValue DataType="http://www.w3.org/2001/XMLSchema#integer">$level</xacml:AttributeValue>
    |</xacml:AttributeAssignmentExpression>
    |</xacml:ObligationExpression>
""".trimMargin()

private fun xml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

// An access list's owner and list are written together as the value, so a subject without both would match no one
private const val ACCESS_LIST_PREFIX = "urn:altinn:access-list"

private val ACCESS_LIST = Regex("""urn:altinn:access-list:[^:\s]+:[^:\s]+""", RegexOption.IGNORE_CASE)

private const val STRING = "http://www.w3.org/2001/XMLSchema#string"

private const val STRING_EQUAL = "urn:oasis:names:tc:xacml:1.0:function:string-equal"

private const val STRING_EQUAL_IGNORE_CASE = "urn:oasis:names:tc:xacml:3.0:function:string-equal-ignore-case"

private const val ACTION_ID = "urn:oasis:names:tc:xacml:1.0:action:action-id"

private const val SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject"

private const val RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource"

private const val ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action"
