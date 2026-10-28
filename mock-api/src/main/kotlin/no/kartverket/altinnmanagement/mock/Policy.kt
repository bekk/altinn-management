package no.kartverket.altinnmanagement.mock

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private const val XACML = "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17"
private const val RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource"
private const val RESOURCE_ATTRIBUTE = "urn:altinn:resource"

/** A policy Altinn's XACML parser cannot read. Altinn answers it with 500. */
class UnreadablePolicyException(message: String) : RuntimeException(message)

/**
 * Altinn's check of a resource's XACML policy: it must be a policy Altinn can read, with at least one rule, and every rule
 * must be about the resource [id] and no other. Altinn does not check the rules' subjects or actions.
 *
 * Returns Altinn's message for a policy it refuses, or null when it accepts it. Throws [UnreadablePolicyException] when
 * Altinn could not read the policy at all.
 */
internal fun policyProblem(policy: ByteArray, id: String): String? {
    for (rule in rules(policy)) {
        val resources = rule.descendants("Match")
            .mapNotNull { match ->
                val designator = match.descendants("AttributeDesignator").firstOrNull() ?: return@mapNotNull null
                val isResource = designator.getAttribute("Category") == RESOURCE_CATEGORY &&
                    designator.getAttribute("AttributeId") == RESOURCE_ATTRIBUTE
                match.descendants("AttributeValue").firstOrNull()?.textContent?.takeIf { isResource }
            }
        if (resources.any { it != id }) return "Policy not accepted: Contains rules for a different registry resource"
        if (id !in resources) return "Policy not accepted: Contains rule without reference to registry resource id"
    }
    return null
}

private fun rules(policy: ByteArray): List<Element> {
    val root = try {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(ByteArrayInputStream(policy)).documentElement
    } catch (e: Exception) {
        throw UnreadablePolicyException("Not XML: ${e.message}")
    }
    if (root.namespaceURI != XACML || root.localName != "Policy") throw UnreadablePolicyException("The root is not a XACML 3.0 Policy")
    if (!root.hasAttribute("PolicyId") || !root.hasAttribute("RuleCombiningAlgId")) {
        throw UnreadablePolicyException("The policy has no PolicyId or RuleCombiningAlgId")
    }
    val rules = root.children("Rule")
    if (rules.isEmpty()) throw UnreadablePolicyException("The policy has no rules")
    for (rule in rules) {
        if (rule.getAttribute("RuleId").isEmpty() || rule.getAttribute("Effect").isEmpty()) {
            throw UnreadablePolicyException("A rule has no RuleId or Effect")
        }
        if (!isEffect(rule.getAttribute("Effect"))) throw UnreadablePolicyException("A rule's Effect is not Permit or Deny")
    }
    if (root.descendants("AnyOf").any { it.children("AllOf").isEmpty() }) throw UnreadablePolicyException("An AnyOf has no AllOf")
    if (root.descendants("AllOf").any { it.children("Match").isEmpty() }) throw UnreadablePolicyException("An AllOf has no Match")
    return rules
}

// Altinn parses the effect as an enum: Permit or Deny in any case, or the enum's number
private fun isEffect(value: String) = value.trim().let { it.equals("Permit", ignoreCase = true) || it.equals("Deny", ignoreCase = true) || it.toIntOrNull() != null }

private fun Element.children(name: String) = (0 until childNodes.length).map { childNodes.item(it) }
    .filterIsInstance<Element>().filter { it.namespaceURI == XACML && it.localName == name }

private fun Element.descendants(name: String) = getElementsByTagNameNS(XACML, name).let { nodes ->
    (0 until nodes.length).map { nodes.item(it) as Element }
}
