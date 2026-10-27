package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceRegistryTest {

    private val path = "/resourceregistry/api/v1/resource"

    private fun ids(body: JsonElement) =
        body.jsonArray.map { it.jsonObject.getValue("identifier").jsonPrimitive.content }

    @Test
    fun `the seed holds Kartverket's three resources and one of another organisation`() = mockApi {
        val all = client.get("$path/search").json()

        assertEquals(listOf("kv-matrikkel-brukernavn", "nrl-reporting", "nrl-retrieval", "slf-skogtiltakws"), ids(all))
    }

    @Test
    fun `a resource is found by its id, with its version and timestamps`() = mockApi {
        val response = client.get("$path/nrl-reporting")
        val body = response.json().jsonObject

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("nrl-reporting", body.getValue("identifier").jsonPrimitive.content)
        assertEquals(115, body.getValue("versionId").jsonPrimitive.content.toInt())
        assertEquals("2024-05-10T06:04:32.656254+00:00", body.getValue("createdAt").jsonPrimitive.content)
    }

    @Test
    fun `an unknown id is a 404 ProblemDetails, sent as application json`() = mockApi {
        val response = client.get("$path/no-such-resource")
        val body = response.json().jsonObject

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("Not Found", body.getValue("title").jsonPrimitive.content)
        assertEquals(404, body.getValue("status").jsonPrimitive.content.toInt())
    }

    @Test
    fun `the id must match exactly, case included`() = mockApi {
        assertEquals(HttpStatusCode.NotFound, client.get("$path/NRL-REPORTING").status)
    }

    @Test
    fun `the newest version is returned, and versionId picks an older one of the same resource`() {
        val registry = ResourceRegistry(listOf(resource("kv-a", 1), resource("kv-a", 7), resource("kv-b", 4)))
        mockApi(registry) {
            assertEquals(7, client.get("$path/kv-a").json().jsonObject.getValue("versionId").jsonPrimitive.content.toInt())
            assertEquals(1, client.get("$path/kv-a?versionId=1").json().jsonObject.getValue("versionId").jsonPrimitive.content.toInt())
            assertEquals(HttpStatusCode.NotFound, client.get("$path/kv-a?versionId=4").status)
        }
    }

    @Test
    fun `createdAt is when the first version was made, updatedAt when this one was`() {
        val registry = ResourceRegistry(listOf(resource("kv-a", 1), resource("kv-a", 7)))
        mockApi(registry) {
            val body = client.get("$path/kv-a").json().jsonObject

            assertEquals("2026-01-02T00:00:00+00:00", body.getValue("createdAt").jsonPrimitive.content)
            assertEquals("2026-02-08T00:00:00+00:00", body.getValue("updatedAt").jsonPrimitive.content)
        }
    }

    @Test
    fun `a versionId that is not a number is a validation problem`() = mockApi {
        val response = client.get("$path/nrl-reporting?versionId=x")
        val errors = response.json().jsonObject.getValue("errors").jsonObject

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("The value 'x' is not valid.", errors.getValue("versionId").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `search by resource type and organisation finds Kartverket's resources, ordered by id`() = mockApi {
        val body = client.get("$path/search?ResourceType=MaskinportenSchema&OrganizationId=971040238").json()

        assertEquals(listOf("kv-matrikkel-brukernavn", "nrl-reporting", "nrl-retrieval"), ids(body))
    }

    @Test
    fun `the search path and the parameter names ignore case`() = mockApi {
        val body = client.get("$path/Search?organizationid=971040238&RESOURCETYPE=maskinportenschema").json()

        assertEquals(3, body.jsonArray.size)
    }

    @Test
    fun `the resource type can also be given as its number`() = mockApi {
        assertEquals(4, client.get("$path/search?resourceType=2").json().jsonArray.size)
        assertEquals(0, client.get("$path/search?resourceType=8").json().jsonArray.size)
    }

    @Test
    fun `a resource type Altinn does not know is a validation problem, worded like Altinn's`() = mockApi {
        val refused = mapOf(
            "not-a-type" to "The value 'not-a-type' is not valid for ResourceType.",
            "3" to "The value '3' is invalid.",
        )
        for ((value, message) in refused) {
            val response = client.get("$path/search?resourceType=$value")
            val errors = response.json().jsonObject.getValue("errors").jsonObject

            assertEquals(HttpStatusCode.BadRequest, response.status, value)
            assertEquals(message, errors.getValue("ResourceType").jsonArray.single().jsonPrimitive.content, value)
        }
    }

    @Test
    fun `reference matches part of any reference, ignoring case`() = mockApi {
        val body = client.get("$path/search?reference=NRL.rap").json()

        assertEquals(listOf("nrl-reporting", "nrl-retrieval"), ids(body))
    }

    @Test
    fun `reference matches references of every type, not only Maskinporten scopes`() {
        val other = buildJsonArray {
            add(buildJsonObject { put("referenceSource", "Altinn2"); put("reference", "kartverk:x"); put("referenceType", "ServiceCode") })
        }
        val registry = ResourceRegistry(listOf(resource("kv-a", 1, scope = "kartverk:a", extra = mapOf("resourceReferences" to other))))
        mockApi(registry) {
            assertEquals(listOf("kv-a"), ids(client.get("$path/search?reference=kartverk:x").json()))
        }
    }

    @Test
    fun `search returns the newest version that matches, even when a newer version does not`() {
        val registry = ResourceRegistry(listOf(resource("kv-a", 1, scope = "kartverk:old"), resource("kv-a", 2, scope = "kartverk:new")))
        mockApi(registry) {
            val body = client.get("$path/search?reference=kartverk:old").json()

            assertEquals(1, body.jsonArray.single().jsonObject.getValue("versionId").jsonPrimitive.content.toInt())
        }
    }

    @Test
    fun `search ignores title, and an empty value is no filter`() = mockApi {
        assertEquals(4, client.get("$path/search?title=nothing-like-this&organizationId=").json().jsonArray.size)
    }

    @Test
    fun `search filters on id, org code, description and keyword too`() = mockApi {
        assertEquals(listOf("nrl-reporting", "nrl-retrieval"), ids(client.get("$path/search?id=NRL").json()))
        assertEquals(listOf("slf-skogtiltakws"), ids(client.get("$path/search?orgCode=SLF").json()))
        assertEquals(listOf("kv-matrikkel-brukernavn"), ids(client.get("$path/search?description=soap-basert").json()))
        assertEquals(listOf("kv-matrikkel-brukernavn"), ids(client.get("$path/search?keyword=matrikkel").json()))
    }

    @Test
    fun `search sets authorizationReference to the resource itself`() {
        val stored = buildJsonArray { add(buildJsonObject { put("id", "something"); put("value", "else") }) }
        val registry = ResourceRegistry(listOf(resource("kv-a", 1, extra = mapOf("authorizationReference" to stored))))
        mockApi(registry) {
            val found = client.get("$path/search").json().jsonArray.single().jsonObject
            val fetched = client.get("$path/kv-a").json().jsonObject

            assertEquals(JsonArray(listOf(buildJsonObject { put("id", "urn:altinn:resource"); put("value", "kv-a") })), found.getValue("authorizationReference"))
            assertEquals(stored, fetched.getValue("authorizationReference"))
        }
    }

    @Test
    fun `no match is an empty list`() = mockApi {
        assertEquals(JsonArray(emptyList()), client.get("$path/search?reference=nothing-like-this").json())
    }
}
