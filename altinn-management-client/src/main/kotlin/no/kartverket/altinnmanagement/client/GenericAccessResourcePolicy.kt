package no.kartverket.altinnmanagement.client

import no.kartverket.altinnmanagement.client.exception.UnsupportedPolicyException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.DocumentBuilderFactory

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
        levels.endUser?.let { obligation(1, END_USER_LEVEL, it) },
        levels.organisation?.let { obligation(2, ORGANISATION_LEVEL, it) },
        levels.systemUser?.let { obligation(3, SYSTEM_USER_LEVEL, it) },
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
    val resource = match(STRING_EQUAL, id.value, RESOURCE_ID, RESOURCE_CATEGORY)
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
    val split = if (subject.startsWith(ACCESS_LIST_PREFIX)) {
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
    |<xacml:AttributeValue DataType="$INTEGER">$level</xacml:AttributeValue>
    |</xacml:AttributeAssignmentExpression>
    |</xacml:ObligationExpression>
""".trimMargin()

// An XML parser turns a line break it reads into \n, and line breaks and tabs in an attribute into spaces
private fun xml(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        .replace("\t", "&#9;").replace("\n", "&#10;").replace("\r", "&#13;")

// An access list's owner and list are written together as the value, so a subject without both would match no one. So
// would a prefix in another case, since Altinn's PDP matches attribute ids by case
private const val ACCESS_LIST_PREFIX = "urn:altinn:access-list"

private val ACCESS_LIST = Regex("""urn:altinn:access-list:[^:\s]+:[^:\s]+""")

// Reads only what genericAccessResourcePolicy writes, since a policy shown without a part could be sent back without it
internal fun parseGenericAccessResourcePolicy(id: ResourceId, xml: ByteArray): GenericAccessResourcePolicy {
    val policy = parseXml(xml).documentElement
    if (policy.namespaceURI != XACML || policy.localName != "Policy") unsupported("its root is ${policy.tagName}, not a Policy")
    val parts = policy.xacmlChildren()
    parts.firstOrNull { it.localName !in POLICY_PARTS }?.let { unsupported("it has a ${it.localName}") }
    if (parts.any { it.localName == "Target" && it.xacmlChildren().isNotEmpty() }) unsupported("the policy has a target")
    val levels = parts.filter { it.localName == "ObligationExpressions" }.flatMap { it.children("ObligationExpression") }.map(::level)
    if (levels.map { it.first }.toSet().size != levels.size) unsupported("it has an authentication level twice")
    val level = levels.toMap()
    // Altinn takes policies that the checks in init refuse, like one with an access list without its owner
    return try {
        GenericAccessResourcePolicy(
            minimumAuthenticationLevel = AuthenticationLevels(level[END_USER_LEVEL], level[ORGANISATION_LEVEL], level[SYSTEM_USER_LEVEL]),
            rules = parts.filter { it.localName == "Rule" }.map { parseRule(id, it) },
        )
    } catch (e: IllegalArgumentException) {
        throw UnsupportedPolicyException(e.message.orEmpty().replaceFirstChar(Char::lowercaseChar), e)
    }
}

private fun parseXml(xml: ByteArray): Document = try {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    factory.newDocumentBuilder().apply { setErrorHandler(DefaultHandler()) }.parse(xml.inputStream())
} catch (e: SAXException) {
    throw UnsupportedPolicyException("it is not XML", e)
}

private fun level(obligation: Element): Pair<String, Int> {
    val assignment = obligation.children("AttributeAssignmentExpression").singleOrNull()
    val value = assignment?.children("AttributeValue")?.singleOrNull()?.takeIf { it.getAttribute("DataType") == INTEGER }
    val category = assignment?.getAttribute("Category")
    val level = value?.textContent?.trim()?.toIntOrNull()
    if (obligation.getAttribute("FulfillOn") != "Permit" || category == null || category !in LEVELS || level == null) {
        unsupported("it has an obligation that is not an authentication level")
    }
    return category to level
}

private fun parseRule(id: ResourceId, rule: Element): PolicyRule {
    val parts = rule.xacmlChildren()
    val effect = PolicyEffect.entries.find { it.value == rule.getAttribute("Effect") } ?: unsupported("a rule neither permits nor denies")
    parts.firstOrNull { it.localName != "Description" && it.localName != "Target" }?.let { unsupported("a rule has a ${it.localName}") }
    val anyOfs = parts.filter { it.localName == "Target" }.flatMap { it.children("AnyOf") }.map { anyOf ->
        anyOf.children("AllOf").map { allOf -> allOf.children("Match").map(::parseMatch) }
    }
    // Several AnyOfs for one category should all hold, but Altinn keeps only one of them (tested in TT02)
    val byCategory = anyOfs.groupBy { it.flatten().map(Match::category).toSet().singleOrNull() ?: unsupported("a rule mixes categories in one AnyOf") }
    fun alternatives(category: String) = byCategory[category]?.singleOrNull() ?: unsupported("a rule does not have one AnyOf for $category")
    if (byCategory.keys != setOf(SUBJECT_CATEGORY, RESOURCE_CATEGORY, ACTION_CATEGORY)) unsupported("a rule matches on other categories")

    val subjects = alternatives(SUBJECT_CATEGORY).map { group ->
        if (group.isEmpty() || group.any { it.function != STRING_EQUAL_IGNORE_CASE }) unsupported("a rule has a group of subjects that is empty or not matched by $STRING_EQUAL_IGNORE_CASE")
        group.map { "${it.attributeId}:${it.value}" }
    }
    val actions = alternatives(ACTION_CATEGORY).map { allOf ->
        allOf.singleOrNull()?.takeIf { it.function == STRING_EQUAL_IGNORE_CASE && it.attributeId == ACTION_ID }?.value
            ?: unsupported("a rule has an action that is not one match on $ACTION_ID with $STRING_EQUAL_IGNORE_CASE")
    }
    val subResources = alternatives(RESOURCE_CATEGORY).map { matches ->
        if (matches.any { it.function != STRING_EQUAL }) unsupported("a rule matches the resource with another function")
        val (resource, subResource) = matches.partition { it.attributeId == RESOURCE_ID }
        if (resource.singleOrNull()?.value != id.value) unsupported("a rule does not name this resource once")
        if (subResource.map { it.attributeId }.toSet().size != subResource.size) unsupported("a rule matches one attribute twice")
        subResource.associate { it.attributeId to it.value }
    }
    return PolicyRule(
        subjects = subjects,
        actions = actions,
        subResources = subResources.takeUnless { it == listOf(emptyMap<String, String>()) }.orEmpty(),
        description = parts.firstOrNull { it.localName == "Description" }?.textContent?.ifEmpty { null },
        effect = effect,
    )
}

private class Match(val function: String, val category: String, val attributeId: String, val value: String)

private fun parseMatch(match: Element): Match {
    val value = match.xacmlChildren().singleOrNull { it.localName == "AttributeValue" }
    val designator = match.xacmlChildren().singleOrNull { it.localName == "AttributeDesignator" }
    if (match.xacmlChildren().size != 2 || value == null || designator == null) unsupported("a match is not a value and an attribute")
    if (value.getAttribute("DataType") != STRING || designator.getAttribute("DataType") != STRING) unsupported("a match compares something other than text")
    return Match(match.getAttribute("MatchId"), designator.getAttribute("Category"), designator.getAttribute("AttributeId"), value.textContent)
}

private fun Element.children(name: String): List<Element> =
    xacmlChildren().onEach { if (it.localName != name) unsupported("it has a ${it.localName} where a $name belongs") }

private fun Element.xacmlChildren(): List<Element> = (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()
    .onEach { if (it.namespaceURI != XACML) unsupported("it has ${it.tagName}, which is not XACML") }

private fun unsupported(reason: String): Nothing = throw UnsupportedPolicyException(reason)

private const val XACML = "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17"

private val POLICY_PARTS = setOf("Target", "Rule", "ObligationExpressions")

private const val END_USER_LEVEL = "urn:altinn:minimum-authenticationlevel"

private const val ORGANISATION_LEVEL = "urn:altinn:minimum-authenticationlevel-org"

private const val SYSTEM_USER_LEVEL = "urn:altinn:minimum-authenticationlevel-systemuser"

private val LEVELS = setOf(END_USER_LEVEL, ORGANISATION_LEVEL, SYSTEM_USER_LEVEL)

private const val RESOURCE_ID = "urn:altinn:resource"

private const val INTEGER = "http://www.w3.org/2001/XMLSchema#integer"

private const val STRING = "http://www.w3.org/2001/XMLSchema#string"

private const val STRING_EQUAL = "urn:oasis:names:tc:xacml:1.0:function:string-equal"

private const val STRING_EQUAL_IGNORE_CASE = "urn:oasis:names:tc:xacml:3.0:function:string-equal-ignore-case"

private const val ACTION_ID = "urn:oasis:names:tc:xacml:1.0:action:action-id"

private const val SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject"

private const val RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource"

private const val ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action"
