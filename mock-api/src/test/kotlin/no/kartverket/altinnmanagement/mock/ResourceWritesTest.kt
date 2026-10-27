package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceWritesTest {

    private val path = "/resourceregistry/api/v1/resource"

    private fun texts(id: String) = buildJsonObject { put("en", "$id en"); put("nb", "$id nb"); put("nn", "$id nn") }

    /** A MaskinportenSchema resource that Altinn accepts from Kartverket. */
    private fun body(
        id: String = "kv-test",
        scope: String = "kartverk:test",
        organization: String = KARTVERKET,
        orgcode: String = "kv",
        change: MutableMap<String, JsonElement>.() -> Unit = {},
    ): JsonObject {
        val fields = mutableMapOf<String, JsonElement>(
            "identifier" to JsonPrimitive(id),
            "title" to texts(id),
            "description" to texts(id),
            "rightDescription" to texts(id),
            "contactPoints" to JsonArray(emptyList()),
            "resourceType" to JsonPrimitive("MaskinportenSchema"),
            "resourceReferences" to buildJsonArray {
                add(buildJsonObject { put("referenceSource", "Default"); put("reference", scope); put("referenceType", "MaskinportenScope") })
            },
            "hasCompetentAuthority" to buildJsonObject { put("organization", organization); put("orgcode", orgcode) },
        )
        fields.change()
        return JsonObject(fields)
    }

    private suspend fun ApplicationTestBuilder.create(body: JsonElement, token: String? = null): HttpResponse {
        val bearer = token ?: altinnToken()
        return client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    private suspend fun ApplicationTestBuilder.update(id: String, body: JsonElement, token: String? = null): HttpResponse {
        val bearer = token ?: altinnToken()
        return client.put("$path/$id") {
            header(HttpHeaders.Authorization, "Bearer $bearer")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    private suspend fun ApplicationTestBuilder.fetch(id: String) = client.get("$path/$id").json().jsonObject

    private suspend fun HttpResponse.errors() = json().jsonObject.getValue("errors").jsonObject
        .mapValues { (_, messages) -> messages.jsonArray.map { it.jsonPrimitive.content } }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    @Test
    fun `a new resource is created with 201, a Location and no body, and can then be read`() = mockApi {
        val response = create(body())

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("$path/kv-test", response.headers[HttpHeaders.Location])
        assertEquals("", response.bodyAsText())
        assertEquals("kv-test nb", fetch("kv-test").getValue("title").jsonObject.text("nb"))
    }

    @Test
    fun `Altinn sets the version and timestamps, and the defaults of omitted fields`() = mockApi {
        create(body { put("versionId", JsonPrimitive(1)); put("createdAt", JsonPrimitive("2000-01-01T00:00:00+00:00")) })
        val stored = fetch("kv-test")

        assertEquals("3775", stored.text("versionId"), "the seed's highest is 3774, and the count is shared by all resources")
        assertEquals(stored.text("createdAt"), stored.text("updatedAt"))
        assertFalse(stored.text("createdAt").startsWith("2000"))
        assertEquals("true", stored.text("delegable"))
        assertEquals("true", stored.text("visible"))
        assertEquals("Disabled", stored.text("accessListMode"))
    }

    @Test
    fun `field names may have any case, enums may be numbers, and unknown fields and nulls are dropped`() = mockApi {
        create(
            body {
                put("Status", JsonPrimitive("Active"))
                put("resourceType", JsonPrimitive(2))
                put("somethingElse", JsonPrimitive("x"))
                put("homepage", JsonNull)
            },
        )
        val stored = fetch("kv-test")

        assertEquals("Active", stored.text("status"))
        assertEquals("MaskinportenSchema", stored.text("resourceType"))
        assertFalse("somethingElse" in stored)
        assertFalse("homepage" in stored)
    }

    @Test
    fun `an enum value Altinn does not know cannot be read`() = mockApi {
        val response = create(body { put("resourceType", JsonPrimitive("Spaceship")) })

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(listOf("$.resourceType"), response.errors().keys.toList())
    }

    @Test
    fun `a write needs an Altinn token, and one with a scope to write`() = mockApi {
        val noToken = client.post(path) { contentType(ContentType.Application.Json); setBody(body().toString()) }
        val maskinporten = create(body(), token = grant())
        val readOnly = create(body(), token = altinnToken(scope = "altinn:resourceregistry/resource.read"))

        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
        assertEquals(HttpStatusCode.Unauthorized, maskinporten.status)
        assertEquals(HttpStatusCode.Forbidden, readOnly.status)
        assertEquals("", readOnly.bodyAsText())
    }

    @Test
    fun `a body that is not JSON is unsupported`() = mockApi {
        val token = altinnToken()
        val response = client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Text.Plain)
            setBody(body().toString())
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun `Altinn's checks of a new resource come in one answer, with Altinn's keys and messages`() = mockApi {
        val response = create(
            buildJsonObject {
                put("identifier", "KV")
                putJsonObject("title") { put("en", "Title") }
                put("resourceType", "MaskinportenSchema")
                putJsonObject("hasCompetentAuthority") { put("organization", KARTVERKET); put("orgcode", "xx") }
            },
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            mapOf(
                "HasCompetentAuthority.Organization" to listOf("HasCompetentAuthority needs to be set with valid organization number and orgcode"),
                "Identifier" to listOf("Invalid id. Only a-z and 0-9 is allowed together with _ and -.  Minimum 4 characters"),
                "Title" to listOf("Missing title in bokmal nb", "Missing title in nynorsk nn"),
                "RightDescription" to listOf("Missing RightDescription in english en", "Missing RightDescription in bokmal nb", "Missing RightDescription in nynorsk nn"),
                "Description" to listOf("Missing Description in english en", "Missing Description in bokmal nb", "Missing Description in nynorsk nn"),
                "ResourceReferences" to listOf("Missing maskinporten scopen for MaskinportenSchema resource"),
            ),
            response.errors(),
        )
    }

    @Test
    fun `a resource that cannot be delegated needs no right description`() = mockApi {
        val response = create(body { remove("rightDescription"); put("delegable", JsonPrimitive(false)) })

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `an organisation number that is not 9 digits makes Altinn fail`() = mockApi {
        val response = create(body(organization = "97104023"))

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
    }

    @Test
    fun `a resource of another organisation is forbidden, but only after Altinn's checks of the resource`() = mockApi {
        val otherOwner = create(body(organization = "981544315", orgcode = "slf", scope = "kartverk:test"))
        val otherOwnerAndInvalid = create(body(organization = "981544315", orgcode = "slf") { remove("title") })

        assertEquals(HttpStatusCode.Forbidden, otherOwner.status)
        assertEquals(HttpStatusCode.BadRequest, otherOwnerAndInvalid.status)
    }

    @Test
    fun `a scope must start with Kartverket's prefix, unless the token has the admin scope`() = mockApi {
        val response = create(body(scope = "ldir:test"))
        val asAdmin = create(body(scope = "ldir:test"), token = altinnToken(scope = "altinn:resourceregistry/resource.admin"))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(mapOf("InvalidPrefix" to listOf("ldir:test")), response.errors())
        assertEquals("Not authorized for creating resource with the listed scopes", response.json().jsonObject.text("detail"))
        assertEquals(HttpStatusCode.Created, asAdmin.status)
    }

    @Test
    fun `an identifier that is taken is a conflict, answered as a JSON string`() = mockApi {
        val response = create(body(id = "nrl-reporting"))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("\"The Resource already exist: nrl-reporting\"", response.bodyAsText())
    }

    @Test
    fun `a PUT adds a new version, keeps createdAt and answers 200 without a body`() = mockApi {
        create(body())
        val first = fetch("kv-test")
        val response = update("kv-test", body { put("status", JsonPrimitive("Active")) })
        val second = fetch("kv-test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
        assertEquals("Active", second.text("status"))
        assertTrue(second.text("versionId").toLong() > first.text("versionId").toLong())
        assertEquals(first.text("createdAt"), second.text("createdAt"))
        assertEquals(first.text("versionId"), fetch("kv-test?versionId=${first.text("versionId")}").text("versionId"))
    }

    @Test
    fun `a PUT replaces the whole resource, so an omitted field is gone`() = mockApi {
        create(body { put("homepage", JsonPrimitive("https://kartverket.no")) })
        update("kv-test", body())

        assertFalse("homepage" in fetch("kv-test"))
    }

    @Test
    fun `a PUT of an unknown id is 404, and an id that differs from the body's is refused`() = mockApi {
        assertEquals(HttpStatusCode.NotFound, update("kv-nothing", body(id = "kv-nothing")).status)

        val mismatch = update("nrl-reporting", body(id = "kv-test"))
        assertEquals(HttpStatusCode.BadRequest, mismatch.status)
        assertEquals("\"Id in path does not match ID in resource\"", mismatch.bodyAsText())
    }

    @Test
    fun `a PUT must have title, description, contact points and owner`() = mockApi {
        val response = update("nrl-reporting", body(id = "nrl-reporting") { remove("title"); remove("contactPoints") })

        assertEquals(
            mapOf("Title" to listOf("The Title field is required."), "ContactPoints" to listOf("The ContactPoints field is required.")),
            response.errors(),
        )
    }

    @Test
    fun `a PUT only checks the owner in the body, so it can change a resource another organisation owns`() = mockApi {
        val response = update("slf-skogtiltakws", body(id = "slf-skogtiltakws"))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(KARTVERKET, fetch("slf-skogtiltakws").getValue("hasCompetentAuthority").jsonObject.text("organization"))
    }

    @Test
    fun `a withdrawn resource is still there, and search still finds it`() = mockApi {
        val withdraw = body(id = "nrl-reporting", scope = "kartverk:nrl.rapportering") {
            remove("rightDescription")
            put("delegable", JsonPrimitive(false))
            put("visible", JsonPrimitive(false))
            put("status", JsonPrimitive("Withdrawn"))
        }

        assertEquals(HttpStatusCode.OK, update("nrl-reporting", withdraw).status)
        val stored = fetch("nrl-reporting")
        assertEquals("Withdrawn", stored.text("status"))
        assertEquals("false", stored.text("visible"))
        val found = client.get("$path/search?id=nrl-reporting").json().jsonArray.single().jsonObject
        assertEquals("Withdrawn", found.text("status"))
    }

    @Test
    fun `after a scope changes, search for the old scope finds the old version`() = mockApi {
        create(body(scope = "kartverk:old"))
        update("kv-test", body(scope = "kartverk:new"))
        val found = client.get("$path/search?reference=kartverk:old").json().jsonArray.single().jsonObject

        assertEquals("kartverk:old", found.getValue("resourceReferences").jsonArray.single().jsonObject.text("reference"))
        assertTrue(found.text("versionId").toLong() < fetch("kv-test").text("versionId").toLong())
    }
}
