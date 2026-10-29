package no.kartverket.altinnmanagement.restserver

import no.kartverket.altinnmanagement.client.MaskinportenSchema
import no.kartverket.altinnmanagement.client.MultilingualText
import no.kartverket.altinnmanagement.client.ResourceId
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessFilterTest {

    private val filter = AccessFilter(
        scopes = Filter(exact = listOf("kartverk:dokumentbestilling"), prefix = listOf("kartverk:tilgangsstyring:")),
        resources = Filter(exact = listOf("res-exact"), prefix = listOf("res-prefix-")),
    )

    @Test
    fun `allows a resource in exact, or that starts with a prefix, when it allows its scope`() {
        assertTrue(filter.allows(schema("res-exact", "kartverk:dokumentbestilling")))
        assertTrue(filter.allows(schema("res-prefix-demo", "kartverk:tilgangsstyring:demo.read")))
    }

    @Test
    fun `allows no other resource, even when it allows its scope`() {
        for (id in listOf("res-exact-2", "res-prefix", "kv-matrikkel-brukernavn")) {
            assertFalse(filter.allows(schema(id, "kartverk:dokumentbestilling")), id)
        }
    }

    @Test
    fun `allows a resource only when it allows all its scopes`() {
        assertTrue(filter.allows(schema("res-exact", "kartverk:dokumentbestilling", "kartverk:tilgangsstyring:demo.read")))
        assertFalse(filter.allows(schema("res-exact", "kartverk:dokumentbestilling", "kartverk:matrikkel:brukernavn")))
    }

    @Test
    fun `allows nothing when all lists are empty`() {
        assertFalse(AccessFilter.NOTHING.allows(schema("res-exact", "kartverk:dokumentbestilling")))
    }

    @Test
    fun `refuses a blank prefix, which would allow everything`() {
        for (prefix in listOf("", " ")) {
            val e = assertFailsWith<IllegalArgumentException>("\"$prefix\"") { Filter(emptyList(), listOf(prefix)) }

            assertContains(e.message!!, "must not be blank", message = "\"$prefix\"")
        }
    }

    @Test
    fun `a list is kept whole when filtering is off, and filtered when it is on`() {
        val schemas = listOf(schema("res-exact", "kartverk:dokumentbestilling"), schema("kv-matrikkel-brukernavn", "kartverk:matrikkel:brukernavn"))

        assertEquals(schemas, schemas.allowedBy(null))
        assertEquals(listOf("res-exact"), schemas.allowedBy(filter).map { it.resourceId.value })
    }

    private fun schema(id: String, vararg scopes: String) = MaskinportenSchema(
        resourceId = ResourceId.of(id),
        scopes = scopes.toList(),
        title = noText,
        description = noText,
        delegationDescription = noText,
        homepage = null,
        contactPoints = emptyList(),
        keywords = emptyList(),
        status = null,
        delegable = true,
        visible = true,
    )

    private val noText = MultilingualText(en = null, nb = null, nn = null)
}
