package no.kartverket.altinnmanagement.client

public data class GenericAccessResourcePolicy(
    public val minimumAuthenticationLevel: AuthenticationLevels = AuthenticationLevels(),
    public val rules: List<PolicyRule>,
)

public data class AuthenticationLevels(
    public val endUser: Int? = null,
    public val organisation: Int? = null,
    public val systemUser: Int? = null,
)

/**
 * Lets [subjects], such as `urn:altinn:accesspackage:eksplisitt` or `urn:altinn:rolecode:dagl`, do [actions] on the
 * resource. Each map in [subResources] is one part of the resource the rule covers, as attribute ids and values.
 * Left empty, the rule covers the whole resource.
 */
public data class PolicyRule(
    public val subjects: List<String>,
    public val actions: List<String>,
    public val subResources: List<Map<String, String>> = emptyList(),
    public val description: String? = null,
)

// Written as Altinn Studio writes a policy (PolicyConverter.cs), so Studio reads it the same way
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
    val subjects = rule.subjects.map { subject ->
        val (attributeId, value) = subjectAttribute(subject)
        listOf(match(STRING_EQUAL_IGNORE_CASE, value, attributeId, SUBJECT_CATEGORY))
    }
    val resource = match(STRING_EQUAL, id.value, "urn:altinn:resource", RESOURCE_CATEGORY)
    val resources = rule.subResources.ifEmpty { listOf(emptyMap()) }.map { subResource ->
        listOf(resource) + subResource.map { (attributeId, value) -> match(STRING_EQUAL, value, attributeId, RESOURCE_CATEGORY) }
    }
    val actions = rule.actions.map { listOf(match(STRING_EQUAL_IGNORE_CASE, it, ACTION_ID, ACTION_CATEGORY)) }
    return listOf(
        """<xacml:Rule RuleId="urn:altinn:resource:${id.value}:ruleid:$number" Effect="Permit">""",
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
    require(':' in subject) { "A subject must be a URN, like urn:altinn:accesspackage:eksplisitt" }
    val split = if (subject.startsWith("urn:altinn:access-list:", ignoreCase = true)) {
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

private const val STRING = "http://www.w3.org/2001/XMLSchema#string"

private const val STRING_EQUAL = "urn:oasis:names:tc:xacml:1.0:function:string-equal"

private const val STRING_EQUAL_IGNORE_CASE = "urn:oasis:names:tc:xacml:3.0:function:string-equal-ignore-case"

private const val ACTION_ID = "urn:oasis:names:tc:xacml:1.0:action:action-id"

private const val SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject"

private const val RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource"

private const val ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action"
