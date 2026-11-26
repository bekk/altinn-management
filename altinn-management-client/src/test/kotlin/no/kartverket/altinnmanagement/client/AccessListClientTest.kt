package no.kartverket.altinnmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.auth.AccessToken
import no.kartverket.altinnmanagement.client.auth.AccessTokenProvider
import no.kartverket.altinnmanagement.client.exception.AltinnException
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

    @Test
    fun `refuses to read access lists without an AccessTokenProvider, before asking Altinn`(): Unit = runBlocking {
        val client = client(accessTokenProvider = null) { json(200, "") }

        assertFailsWith<IllegalStateException> { client.listAccessLists() }
        assertFailsWith<IllegalStateException> { client.listAccessLists(geonorge) }
        assertFailsWith<IllegalStateException> { client.getAccessList("kv-a") }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `drops a token Altinn refuses, so the next call gets a new one`(): Unit = runBlocking {
        val client = client { json(401, "") }

        assertFailsWith<AltinnException> { client.listAccessLists() }
        assertFailsWith<AltinnException> { client.listAccessLists(geonorge) }
        assertFailsWith<AltinnException> { client.getAccessList("kv-a") }
        assertEquals(listOf("token", "token", "token"), refused.map { it.value })
    }

    @Test
    fun `fails when Altinn answers with an error or with something else than a list`(): Unit = runBlocking {
        for (answer in listOf(json(500, ""), json(200, "[]"))) {
            assertFailsWith<AltinnException>(answer.toString()) { client { answer }.getAccessList("kv-a") }
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
