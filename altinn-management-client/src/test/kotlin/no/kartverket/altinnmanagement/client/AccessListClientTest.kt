package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.auth.AccessToken
import no.kartverket.altinnmanagement.client.auth.AccessTokenProvider
import no.kartverket.altinnmanagement.client.exception.AccessListAlreadyExistsException
import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.exception.AltinnRejectedException
import no.kartverket.altinnmanagement.client.exception.UnknownOrganisationException
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.client.support.recording
import no.kartverket.altinnmanagement.client.support.response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessListClientTest {

    private val sent = mutableListOf<OutgoingRequest>()
    private val refused = mutableListOf<AccessToken>()

    private val tokens = object : AccessTokenProvider {
        override suspend fun accessToken() = AccessToken.of("token")

        override suspend fun refused(token: AccessToken) {
            refused += token
        }
    }

    private val lists = "https://platform.tt02.altinn.no/resourceregistry/api/v1/access-lists/kv"

    private fun list(id: String, connections: String = "") = """
        {
          "urn": "urn:altinn:access-list:kv:$id",
          "identifier": "$id",
          "name": "Name of $id",
          "description": "",
          "createdAt": "2026-11-20T10:15:30.123456+00:00",
          "updatedAt": "2026-11-21T08:00:00+00:00"$connections
        }
    """.trimIndent()

    // Altinn gives every list its connections to the resource asked for, which is an empty array for most
    private fun listConnectedTo(id: String, vararg resources: String) = list(
        id,
        resources.joinToString(",", """, "resourceConnections": [""", "]") {
            """{"resourceIdentifier":"$it","createdAt":"2026-11-20T10:15:30+00:00","updatedAt":"2026-11-20T10:15:30+00:00"}"""
        },
    )

    private val geonorge = ResourceId.of("kv-geonorge")

    private fun json(status: Int, body: String) = response(status, "application/json", body)

    private val kvNy = AccessList(
        accessListId = "kv-ny",
        urn = "urn:altinn:access-list:kv:kv-ny",
        name = "Name of kv-ny",
        description = "",
        createdAt = "2026-11-20T10:15:30.123456+00:00",
        updatedAt = "2026-11-21T08:00:00+00:00",
    )

    // Every way of changing a list, so that each can be checked for what they share
    private val writes: List<suspend ResourceRegistryClient.() -> Any?> = listOf(
        { createAccessList("kv-ny", "Ny", null) },
        { updateAccessList("kv-ny", "Ny", null) },
        { deleteAccessList("kv-ny") },
    )

    // Every call on a list's members, which do not read what Altinn answers to a change
    private val memberCalls: List<suspend ResourceRegistryClient.() -> Any?> = listOf(
        { listAccessListMembers("kv-ny") },
        { addAccessListMembers("kv-ny", listOf("991825827")) },
        { removeAccessListMembers("kv-ny", listOf("991825827")) },
    )

    private fun client(accessTokenProvider: AccessTokenProvider? = tokens, answer: (OutgoingRequest) -> OutgoingResponse) =
        ResourceRegistryClient(AltinnEnvironment.TT02.platformBaseUrl, recording(sent, answer), accessTokenProvider)

    @Test
    fun `gives the lists from every page, asking for the next page on its own URL with only the link's token`(): Unit = runBlocking {
        val client = client { request ->
            if (request.url.query == null) {
                json(200, """{"links":{"next":"https://elsewhere.example/access-lists/kv?token=InRlc3QyMCI&includes=resources"},"data":[${list("kv-a")}]}""")
            } else {
                json(200, """{"links":{},"data":[${list("kv-b")}]}""")
            }
        }

        assertEquals(listOf("kv-a", "kv-b"), client.listAccessLists().map { it.accessListId })
        assertEquals(listOf(lists, "$lists?token=InRlc3QyMCI"), sent.map { it.url.toString() })
        assertTrue(sent.all { it.headers["Authorization"] == "Bearer token" }, sent.toString())
    }

    @Test
    fun `gives only the lists connected to the resource`(): Unit = runBlocking {
        val client = client {
            json(200, """{"links":{},"data":[${listConnectedTo("kv-a")},${listConnectedTo("kv-b", "kv-geonorge")}]}""")
        }

        assertEquals(listOf("kv-b"), client.listAccessLists(geonorge).map { it.accessListId })
        assertEquals("$lists?include=resources&resource=kv-geonorge", sent.single().url.toString())
    }

    @Test
    fun `asks for every page with the resource, since Altinn's link to the next page leaves it out`(): Unit = runBlocking {
        val client = client { request ->
            if ("token=" !in request.url.query) {
                json(200, """{"links":{"next":"$lists?token=ImtiLWEi&includes=resources"},"data":[${listConnectedTo("kv-a")}]}""")
            } else {
                json(200, """{"links":{},"data":[${listConnectedTo("kv-b", "kv-geonorge")}]}""")
            }
        }

        assertEquals(listOf("kv-b"), client.listAccessLists(geonorge).map { it.accessListId })
        assertEquals(
            listOf(
                "$lists?include=resources&resource=kv-geonorge",
                "$lists?include=resources&resource=kv-geonorge&token=ImtiLWEi",
            ),
            sent.map { it.url.toString() },
        )
    }

    @Test
    fun `creates a list with PUT, telling Altinn to take it only when it has no list with the id`(): Unit = runBlocking {
        val list = client { json(200, list("kv-ny")) }.createAccessList("kv-ny", "Ny", "Om lista")

        assertEquals(kvNy, list)
        val request = sent.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals("$lists/kv-ny", request.url.toString())
        assertEquals("*", request.headers["If-None-Match"])
        assertEquals(null, request.headers["If-Match"])
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("Bearer token", request.headers["Authorization"])
        assertEquals("""{"name":"Ny","description":"Om lista"}""", request.body!!.decodeToString())
    }

    @Test
    fun `fails to create a list when Altinn has one with the id`(): Unit = runBlocking {
        val e = assertFailsWith<AccessListAlreadyExistsException> { client { json(412, "") }.createAccessList("kv-ny", "Ny", null) }

        assertEquals("kv-ny", e.accessListId)
    }

    @Test
    fun `changes a list with PUT, telling Altinn to take it only when it has the list, and empties a left out description`(): Unit =
        runBlocking {
            val list = client { json(200, list("kv-ny")) }.updateAccessList("kv-ny", "Ny", null)

            assertEquals(kvNy, list)
            val request = sent.single()
            assertEquals(HttpMethod.PUT, request.method)
            assertEquals("$lists/kv-ny", request.url.toString())
            assertEquals("*", request.headers["If-Match"])
            assertEquals(null, request.headers["If-None-Match"])
            assertEquals("""{"name":"Ny","description":""}""", request.body!!.decodeToString())
        }

    @Test
    fun `changes no list when Altinn has none with the id`(): Unit = runBlocking {
        assertNull(client { json(412, "") }.updateAccessList("kv-ny", "Ny", null))
    }

    @Test
    fun `deletes a list and gives it as it was`(): Unit = runBlocking {
        val list = client { json(200, list("kv-ny")) }.deleteAccessList("kv-ny")

        assertEquals(kvNy, list)
        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("$lists/kv-ny", request.url.toString())
        assertEquals("Bearer token", request.headers["Authorization"])
    }

    @Test
    fun `deletes no list when Altinn has none with the id`(): Unit = runBlocking {
        assertNull(client { response(204, "application/json", "") }.deleteAccessList("kv-ny"))
    }

    @Test
    fun `keeps the id of a list to change in one path segment`(): Unit = runBlocking {
        val client = client { json(200, list("kv-ny")) }
        client.createAccessList("a/b c", "Ny", null)
        client.updateAccessList("a/b c", "Ny", null)
        client.deleteAccessList("a/b c")

        assertEquals(List(3) { "$lists/a%2Fb%20c" }, sent.map { it.url.toString() })
    }

    @Test
    fun `gives Altinn's reasons when it rejects a change to a list`(): Unit = runBlocking {
        val answer = json(400, """{"title":"Bad Request","status":400,"errors":{"Name":["The Name field is required."]}}""")
        for (write in writes) {
            val e = assertFailsWith<AltinnRejectedException> { client { answer }.write() }
            assertEquals(listOf("Name: The Name field is required."), e.reasons)
        }
    }

    @Test
    fun `gives a list as Altinn has it`(): Unit = runBlocking {
        val list = client { json(200, list("kv-matrikkel-kunder")) }.getAccessList("kv-matrikkel-kunder")

        val expected = AccessList(
            accessListId = "kv-matrikkel-kunder",
            urn = "urn:altinn:access-list:kv:kv-matrikkel-kunder",
            name = "Name of kv-matrikkel-kunder",
            description = "",
            createdAt = "2026-11-20T10:15:30.123456+00:00",
            updatedAt = "2026-11-21T08:00:00+00:00",
        )
        assertEquals(expected, list)
        assertEquals("$lists/kv-matrikkel-kunder", sent.single().url.toString())
    }

    @Test
    fun `keeps an id in one path segment`(): Unit = runBlocking {
        client { json(404, "") }.getAccessList("a/b c")

        assertEquals("$lists/a%2Fb%20c", sent.single().url.toString())
    }

    @Test
    fun `gives no list when Altinn has none with the id`(): Unit = runBlocking {
        assertNull(client { json(404, """{"title":"Not Found","status":404}""") }.getAccessList("kv-finnes-ikke"))
    }

    private fun member(partyUuid: String, organisationNumber: String? = null): String {
        val number = organisationNumber?.let { ""","urn:altinn:organization:identifier-no":"$it"""" }.orEmpty()
        return """{"id":"urn:altinn:party:uuid:$partyUuid","since":"2026-11-20T10:15:30.123456+00:00",""" +
            """"identifiers":{"urn:altinn:party:uuid":"$partyUuid","urn:altinn:party:id":50001234$number}}"""
    }

    @Test
    fun `gives the members from every page, with the organisation number only for an organisation`(): Unit = runBlocking {
        val client = client { request ->
            if (request.url.query == null) {
                json(200, """{"links":{"next":"https://elsewhere.example/members?token=eyJ2Ijo0Mn0"},"data":[${member("a1", "991825827")}]}""")
            } else {
                json(200, """{"links":{},"data":[${member("b2")}]}""")
            }
        }

        val members = client.listAccessListMembers("kv-ny")

        val since = "2026-11-20T10:15:30.123456+00:00"
        assertEquals(listOf(AccessListMember("991825827", since), AccessListMember(null, since)), members)
        assertEquals(listOf("$lists/kv-ny/members", "$lists/kv-ny/members?token=eyJ2Ijo0Mn0"), sent.map { it.url.toString() })
        assertTrue(sent.all { it.headers["Authorization"] == "Bearer token" }, sent.toString())
    }

    @Test
    fun `gives no members when Altinn has no list with the id`(): Unit = runBlocking {
        assertNull(client { json(404, "") }.listAccessListMembers("kv-finnes-ikke"))
    }

    @Test
    fun `adds and removes organisations by their URN, and tells whether Altinn has the list`(): Unit = runBlocking {
        val client = client { request -> json(if ("finnes-ikke" in request.url.path) 404 else 200, """{"links":{},"data":[]}""") }

        assertTrue(client.addAccessListMembers("kv-ny", listOf("991825827", "971040238")))
        assertTrue(client.removeAccessListMembers("kv-ny", listOf("991825827")))
        assertEquals(false, client.addAccessListMembers("kv-finnes-ikke", listOf("991825827")))
        assertEquals(false, client.removeAccessListMembers("kv-finnes-ikke", listOf("991825827")))

        assertEquals(listOf(HttpMethod.POST, HttpMethod.DELETE), sent.take(2).map { it.method })
        assertTrue(sent.take(2).all { it.url.toString() == "$lists/kv-ny/members" }, sent.toString())
        assertTrue(sent.all { it.headers["Authorization"] == "Bearer token" }, sent.toString())
        assertEquals(
            listOf(
                """{"data":["urn:altinn:organization:identifier-no:991825827","urn:altinn:organization:identifier-no:971040238"]}""",
                """{"data":["urn:altinn:organization:identifier-no:991825827"]}""",
            ),
            sent.take(2).map { it.body!!.decodeToString() },
        )
    }

    @Test
    fun `fails with UnknownOrganisationException when Altinn does not know an organisation`(): Unit = runBlocking {
        val client = client {
            json(400, """{"type":"urn:altinn:error:RR-00001","title":"One or more party references not found.","status":400,"code":"RR-00001"}""")
        }

        assertFailsWith<UnknownOrganisationException> { client.addAccessListMembers("kv-ny", listOf("991825827")) }
        assertFailsWith<UnknownOrganisationException> { client.removeAccessListMembers("kv-ny", listOf("991825827")) }
    }

    @Test
    fun `fails when Altinn answers members with an error, something else than members, or a next page without a token`(): Unit =
        runBlocking {
            val answers = listOf(
                json(500, ""),
                json(200, "[]"),
                json(200, """{"links":{"next":"$lists/kv-ny/members"},"data":[]}"""),
            )
            for (answer in answers) {
                assertFailsWith<AltinnException>(answer.toString()) { client { answer }.listAccessListMembers("kv-ny") }
            }
            assertFailsWith<AltinnException> { client { json(500, "") }.addAccessListMembers("kv-ny", listOf("991825827")) }
            assertFailsWith<AltinnException> { client { json(500, "") }.removeAccessListMembers("kv-ny", listOf("991825827")) }
        }

    @Test
    fun `refuses to read access lists without an AccessTokenProvider, before asking Altinn`(): Unit = runBlocking {
        val client = client(accessTokenProvider = null) { json(200, "") }

        assertFailsWith<IllegalStateException> { client.listAccessLists() }
        assertFailsWith<IllegalStateException> { client.listAccessLists(geonorge) }
        assertFailsWith<IllegalStateException> { client.getAccessList("kv-a") }
        for (write in writes + memberCalls) assertFailsWith<IllegalStateException> { client.write() }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `drops a token Altinn refuses, so the next call gets a new one`(): Unit = runBlocking {
        val client = client { json(401, "") }

        assertFailsWith<AltinnException> { client.listAccessLists() }
        assertFailsWith<AltinnException> { client.listAccessLists(geonorge) }
        assertFailsWith<AltinnException> { client.getAccessList("kv-a") }
        for (write in writes + memberCalls) assertFailsWith<AltinnException> { client.write() }
        assertEquals(List(9) { "token" }, refused.map { it.value })
    }

    @Test
    fun `fails when Altinn answers with an error or with something else than a list`(): Unit = runBlocking {
        for (answer in listOf(json(500, ""), json(200, "[]"))) {
            assertFailsWith<AltinnException>(answer.toString()) { client { answer }.getAccessList("kv-a") }
            for (write in writes) assertFailsWith<AltinnException>(answer.toString()) { client { answer }.write() }
        }
    }

    @Test
    fun `fails when Altinn answers a list with an error, something else than lists, or a next page without a token`(): Unit = runBlocking {
        val answers = listOf(
            json(403, ""),
            json(200, "[]"),
            json(200, """{"links":{"next":"https://platform.tt02.altinn.no/resourceregistry/api/v1/access-lists/kv"},"data":[]}"""),
        )
        for (answer in answers) {
            assertFailsWith<AltinnException>(answer.toString()) { client { answer }.listAccessLists() }
            assertFailsWith<AltinnException>(answer.toString()) { client { answer }.listAccessLists(geonorge) }
        }
    }
}
