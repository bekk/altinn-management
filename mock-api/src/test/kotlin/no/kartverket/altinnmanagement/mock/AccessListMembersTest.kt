package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccessListMembersTest {

    private val members = "/resourceregistry/api/v1/access-lists/kv/internt/members"

    private val write = "altinn:resourceregistry/accesslist.write"

    private fun org(number: String) = "\"urn:altinn:organization:identifier-no:$number\""

    private suspend fun ApplicationTestBuilder.change(method: HttpMethod, body: String, url: String = members, scope: String = write) =
        client.request(url) {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer ${altinnToken(scope)}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.read(url: String = members) =
        client.get(url) { header(HttpHeaders.Authorization, "Bearer ${altinnToken("altinn:resourceregistry/accesslist.read")}") }

    private fun JsonObject.organisationNumbers() = getValue("data").jsonArray.map {
        it.jsonObject.getValue("identifiers").jsonObject.getValue("urn:altinn:organization:identifier-no").jsonPrimitive.content
    }

    @Test
    fun `adds organisations, skipping one already on the list, and answers with the members`() = mockApi {
        change(HttpMethod.Post, """{"data":[${org("971040238")}]}""")
        val response = change(HttpMethod.Post, """{"data":[${org("971040238")},${org("991825827")}]}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(setOf("971040238", "991825827"), response.json().jsonObject.organisationNumbers().toSet())
        assertEquals(setOf("971040238", "991825827"), read().json().jsonObject.organisationNumbers().toSet())
    }

    @Test
    fun `gives each member its party uuid, when it was added, and its identifiers`() = mockApi {
        change(HttpMethod.Post, """{"data":[${org("971040238")}]}""")

        val member = read().json().jsonObject.getValue("data").jsonArray.single().jsonObject
        val uuid = member.getValue("identifiers").jsonObject.getValue("urn:altinn:party:uuid").jsonPrimitive.content
        assertEquals("urn:altinn:party:uuid:$uuid", member.getValue("id").jsonPrimitive.content)
        assertTrue(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{6}\+00:00""").matches(member.getValue("since").jsonPrimitive.content))
    }

    @Test
    fun `removes organisations, skipping one not on the list`() = mockApi {
        change(HttpMethod.Post, """{"data":[${org("971040238")},${org("991825827")}]}""")
        val response = change(HttpMethod.Delete, """{"data":[${org("971040238")},${org("311718371")}]}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("991825827"), response.json().jsonObject.organisationNumbers())
    }

    @Test
    fun `takes a party by its uuid or id too`() = mockApi {
        val party = AccessListMembers.seeded().party(PartyUrn.OrganisationNumber("991825827"))!!
        change(HttpMethod.Post, """{"data":["urn:altinn:party:uuid:${party.uuid}"]}""")
        change(HttpMethod.Delete, """{"data":["urn:altinn:party:id:${party.id}"]}""")

        assertEquals(emptyList(), read().json().jsonObject.organisationNumbers())
    }

    @Test
    fun `changes nothing and answers RR-00001 when one organisation is unknown`() = mockApi {
        val response = change(HttpMethod.Post, """{"data":[${org("971040238")},${org("123456789")}]}""")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("RR-00001", response.json().jsonObject.getValue("code").jsonPrimitive.content)
        assertEquals(emptyList(), read().json().jsonObject.organisationNumbers())
    }

    @Test
    fun `answers 400 to more than 100 members or a party URN it cannot read`() = mockApi {
        val tooMany = (0..100).joinToString(",") { org("971040238") }
        for (body in listOf("""{"data":[$tooMany]}""", """{"data":[${org("97104023")}]}""", """{"data":["urn:altinn:person:identifier-no:28897599238"]}""", "{}")) {
            assertEquals(HttpStatusCode.BadRequest, change(HttpMethod.Post, body).status, body)
        }
    }

    @Test
    fun `answers 404 for a list it does not have`() = mockApi {
        val url = "/resourceregistry/api/v1/access-lists/kv/finnes-ikke/members"

        assertEquals(HttpStatusCode.NotFound, read(url).status)
        assertEquals(HttpStatusCode.NotFound, change(HttpMethod.Post, """{"data":[${org("971040238")}]}""", url).status)
        assertEquals(HttpStatusCode.NotFound, change(HttpMethod.Delete, """{"data":[]}""", url).status)
    }

    @Test
    fun `answers 403 to a change without a scope to write lists, or for another owner`() = mockApi {
        val body = """{"data":[${org("971040238")}]}"""

        assertEquals(HttpStatusCode.Forbidden, change(HttpMethod.Post, body, scope = "altinn:resourceregistry/accesslist.read").status)
        assertEquals(HttpStatusCode.Forbidden, change(HttpMethod.Post, body, url = "/resourceregistry/api/v1/access-lists/skd/liste/members").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(members) { setBody(body) }.status)
    }

    @Test
    fun `gives the members 100 at a time, with an absolute link to the next page`() {
        val numbers = (1..150).map { "%09d".format(900_000_000 + it) }
        val parties = numbers.mapIndexed { i, n -> Party("00000000-0000-0000-0000-%012d".format(i), i + 1L, n) }
        val lists = AccessLists(listOf(buildJsonObject { put("identifier", "internt") }), AccessListMembers(parties))
        mockApi(lists = lists) {
            change(HttpMethod.Post, """{"data":[${numbers.take(100).joinToString(",") { org(it) }}]}""")
            change(HttpMethod.Post, """{"data":[${numbers.drop(100).joinToString(",") { org(it) }}]}""")

            val pages = mutableListOf<List<String>>()
            var url: String? = members
            while (url != null) {
                val body = read(url).json().jsonObject
                pages += body.organisationNumbers()
                url = body.getValue("links").jsonObject["next"]?.jsonPrimitive?.content?.let { URI(it).run { "$rawPath?$rawQuery" } }
            }

            assertEquals(listOf(100, 50), pages.map { it.size })
            assertEquals(numbers, pages.flatten())
        }
    }
}
