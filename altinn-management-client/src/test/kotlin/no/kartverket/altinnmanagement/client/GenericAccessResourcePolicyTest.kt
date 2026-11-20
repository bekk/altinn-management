package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.auth.AccessToken
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.resourceJson
import no.kartverket.altinnmanagement.client.support.response
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenericAccessResourcePolicyTest {

    private val id = ResourceId.of("kv-geonorge")

    private val eksplisitt = "urn:altinn:accesspackage:eksplisitt"

    @Test
    fun `writes kv-geonorge's policy as Altinn Studio wrote it in TT02`() {
        val policy = GenericAccessResourcePolicy(
            minimumAuthenticationLevel = AuthenticationLevels(endUser = 4, organisation = 3),
            rules = listOf(
                PolicyRule(subjects = listOf(listOf(eksplisitt)), actions = listOf("access")),
                PolicyRule(
                    subjects = listOf(listOf(eksplisitt)),
                    actions = listOf("download"),
                    subResources = listOf(mapOf("urn:kv:geonorge" to "dataset")),
                ),
            ),
        )

        val expected = parse(javaClass.getResource("/kv-geonorge-policy.xml")!!.readText())
        val actual = parse(genericAccessResourcePolicy(id, policy))
        assertTrue(expected.isEqualNode(actual), genericAccessResourcePolicy(id, policy))
    }

    @Test
    fun `gives each group of subjects, sub-resource and action its own alternative in the rule`() {
        val rule = PolicyRule(
            subjects = listOf(listOf(eksplisitt), listOf("urn:altinn:rolecode:dagl")),
            actions = listOf("read", "write"),
            subResources = listOf(mapOf("urn:kv:geonorge" to "dataset"), mapOf("urn:kv:geonorge" to "service")),
        )

        val anyOfs = elements(parse(xacml(rule)), "AnyOf")

        assertEquals(listOf(2, 2, 2), anyOfs.map { elements(it, "AllOf").size })
        assertEquals(listOf("urn:altinn:accesspackage", "urn:altinn:rolecode"), attributeIds(anyOfs[0]))
        assertEquals(listOf("eksplisitt", "dagl"), values(anyOfs[0]))
        assertEquals(listOf("kv-geonorge", "dataset", "kv-geonorge", "service"), values(anyOfs[1]))
        assertEquals(listOf("read", "write"), values(anyOfs[2]))
    }

    @Test
    fun `takes the owner and the list as the value of an access list`() {
        val subjects = elements(parse(xacml(PolicyRule(listOf(listOf("urn:altinn:access-list:kv:testliste")), listOf("read")))), "AnyOf")[0]

        assertEquals(listOf("urn:altinn:access-list"), attributeIds(subjects))
        assertEquals(listOf("kv:testliste"), values(subjects))
    }

    @Test
    fun `writes a level for system users when there is one`() {
        val policy = GenericAccessResourcePolicy(AuthenticationLevels(systemUser = 2), listOf(PolicyRule(listOf(listOf(eksplisitt)), listOf("read"))))

        val assignment = elements(parse(genericAccessResourcePolicy(id, policy)), "AttributeAssignmentExpression").single()

        assertEquals("urn:altinn:minimum-authenticationlevel-systemuser", assignment.getAttribute("Category"))
        assertEquals("2", assignment.textContent.trim())
    }

    @Test
    fun `writes no levels when there are none`() {
        val policy = GenericAccessResourcePolicy(rules = listOf(PolicyRule(listOf(listOf(eksplisitt)), listOf("read"))))

        assertEquals(emptyList(), elements(parse(genericAccessResourcePolicy(id, policy)), "ObligationExpressions"))
    }

    @Test
    fun `keeps text with XML characters as it was given`() {
        val description = """Only <dataset> & "service" | 'metadata'"""
        val rule = PolicyRule(listOf(listOf(eksplisitt)), listOf("read & write"), listOf(mapOf("urn:kv:geonorge" to "a<b")), description)

        val document = parse(xacml(rule))

        assertEquals(description, elements(document, "Description").single().textContent)
        assertEquals(listOf("read & write"), values(elements(document, "AnyOf")[2]))
        assertEquals(listOf("kv-geonorge", "a<b"), values(elements(document, "AnyOf")[1]))
    }

    @Test
    fun `keeps line breaks and tabs as they were given`() {
        val description = "First line\r\nSecond\tline"
        val rule = PolicyRule(listOf(listOf(eksplisitt)), listOf("read"), listOf(mapOf("urn:kv:geo\tnorge\r\n" to "a\rb\nc\td")), description)

        val document = parse(xacml(rule))

        assertEquals(description, elements(document, "Description").single().textContent)
        assertEquals(listOf("urn:altinn:resource", "urn:kv:geo\tnorge\r\n"), attributeIds(elements(document, "AnyOf")[1]))
        assertEquals(listOf("kv-geonorge", "a\rb\nc\td"), values(elements(document, "AnyOf")[1]))
    }

    @Test
    fun `puts a group of subjects in one alternative, so a user must match all of them`() {
        val rule = PolicyRule(subjects = listOf(listOf(eksplisitt, "urn:altinn:rolecode:dagl"), listOf("urn:altinn:rolecode:lede")), actions = listOf("read"))

        val subjects = elements(parse(xacml(rule)), "AnyOf")[0]

        assertEquals(listOf(2, 1), elements(subjects, "AllOf").map { elements(it, "Match").size })
        assertEquals(listOf("eksplisitt", "dagl", "lede"), values(subjects))
    }

    @Test
    fun `writes the effect of each rule`() {
        val policy = GenericAccessResourcePolicy(
            rules = listOf(
                PolicyRule(listOf(listOf(eksplisitt)), listOf("read")),
                PolicyRule(listOf(listOf("urn:altinn:rolecode:dagl")), listOf("read"), effect = PolicyEffect.DENY),
            ),
        )

        val rules = elements(parse(genericAccessResourcePolicy(id, policy)), "Rule")

        assertEquals(listOf("Permit", "Deny"), rules.map { it.getAttribute("Effect") })
    }

    @Test
    fun `refuses a subject that is not a URN`() {
        for (subject in listOf("eksplisitt", "urn:altinn:rolecode:", ":dagl")) {
            assertFailsWith<IllegalArgumentException>(subject) { PolicyRule(listOf(listOf(subject)), listOf("read")) }
        }
    }

    @Test
    fun `refuses a blank action`() {
        assertFailsWith<IllegalArgumentException> { PolicyRule(listOf(listOf(eksplisitt)), listOf(" ")) }
    }

    @Test
    fun `refuses a sub-resource that is empty or has a blank attribute id or value`() {
        for (subResource in listOf(emptyMap(), mapOf(" " to "dataset"), mapOf("urn:kv:geonorge" to ""))) {
            assertFailsWith<IllegalArgumentException>(subResource.toString()) {
                PolicyRule(listOf(listOf(eksplisitt)), listOf("read"), listOf(subResource, mapOf("urn:kv:geonorge" to "dataset")))
            }
        }
    }

    @Test
    fun `refuses an access list without both its owner and the list`() {
        for (subject in listOf("urn:altinn:access-list:sperreliste", "urn:altinn:access-list:kv:", "urn:altinn:access-list")) {
            assertFailsWith<IllegalArgumentException>(subject) { PolicyRule(listOf(listOf(subject)), listOf("read")) }
        }
    }

    @Test
    fun `refuses an access list whose prefix is not in lower case`() {
        assertFailsWith<IllegalArgumentException> { PolicyRule(listOf(listOf("URN:Altinn:Access-List:kv:testliste")), listOf("read")) }
    }

    @Test
    fun `refuses a policy without rules`() {
        assertFailsWith<IllegalArgumentException> { GenericAccessResourcePolicy(rules = emptyList()) }
    }

    @Test
    fun `refuses a rule without subjects or actions, or with an empty group of subjects`() {
        assertFailsWith<IllegalArgumentException> { PolicyRule(emptyList(), listOf("read")) }
        assertFailsWith<IllegalArgumentException> { PolicyRule(listOf(emptyList()), listOf("read")) }
        assertFailsWith<IllegalArgumentException> { PolicyRule(listOf(listOf(eksplisitt)), emptyList()) }
    }

    @Test
    fun `sends the policy with a boundary it does not contain`(): Unit = runBlocking {
        val sent = mutableListOf<OutgoingRequest>()
        val client = ResourceRegistryClient(
            AltinnEnvironment.TT02.platformBaseUrl,
            OutgoingHttpClient { request ->
                sent += request
                if (request.method == HttpMethod.GET) response(200, "application/json", resourceJson("GenericAccessResource")) else response(201, "", "")
            },
        ) { AccessToken.of("token") }
        val rule = PolicyRule(listOf(listOf(eksplisitt)), listOf("read"), description = "--AltinnManagementPolicyBoundary--")

        assertTrue(client.setGenericAccessResourcePolicy(ResourceId.of("kv-matrikkel-brukernavn"), GenericAccessResourcePolicy(rules = listOf(rule))))

        val put = sent.single { it.method == HttpMethod.PUT }
        val boundary = "AltinnManagementPolicyBoundary---"
        assertEquals("multipart/form-data; boundary=$boundary", put.headers["Content-Type"])
        val body = put.body!!.decodeToString()
        assertTrue(body.startsWith("--$boundary\r\n") && body.endsWith("\r\n--$boundary--\r\n"), body)
    }

    private fun xacml(rule: PolicyRule) = genericAccessResourcePolicy(id, GenericAccessResourcePolicy(rules = listOf(rule)))

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringElementContentWhitespace = true
        }
        return factory.newDocumentBuilder().parse(xml.byteInputStream()).apply { removeBlankText(documentElement) }
    }

    private fun removeBlankText(node: Node) {
        for (child in (0 until node.childNodes.length).map(node.childNodes::item)) {
            if (child.nodeType == Node.TEXT_NODE && child.textContent.isBlank()) node.removeChild(child) else removeBlankText(child)
        }
    }

    private fun elements(node: Node, name: String): List<Element> {
        val found = if (node is Document) node.getElementsByTagNameNS(XACML, name) else (node as Element).getElementsByTagNameNS(XACML, name)
        return (0 until found.length).map { found.item(it) as Element }
    }

    private fun attributeIds(anyOf: Element) = elements(anyOf, "AttributeDesignator").map { it.getAttribute("AttributeId") }

    private fun values(anyOf: Element) = elements(anyOf, "AttributeValue").map { it.textContent }

    private companion object {
        const val XACML = "urn:oasis:names:tc:xacml:3.0:core:schema:wd-17"
    }
}
