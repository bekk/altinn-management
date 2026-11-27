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

    private fun list(id: String) = """
        {
          "urn": "urn:altinn:access-list:kv:$id",
          "identifier": "$id",
          "name": "Name of $id",
          "description": "",
          "createdAt": "2026-11-20T10:15:30.123456+00:00",
          "updatedAt": "2026-11-21T08:00:00+00:00"
        }
    """.trimIndent()

    private fun json(status: Int, body: String) = response(status, "application/json", body)

    private fun client(accessTokenProvider: AccessTokenProvider? = tokens, answer: (OutgoingRequest) -> OutgoingResponse) =
        ResourceRegistryClient(AltinnEnvironment.TT02.platformBaseUrl, recording(sent, answer), accessTokenProvider)

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

        assertFailsWith<IllegalStateException> { client.getAccessList("kv-a") }
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `drops a token Altinn refuses, so the next call gets a new one`(): Unit = runBlocking {
        val client = client { json(401, "") }

        assertFailsWith<AltinnException> { client.getAccessList("kv-a") }
        assertEquals(listOf("token"), refused.map { it.value })
    }

    @Test
    fun `fails when Altinn answers with an error or with something else than a list`(): Unit = runBlocking {
        for (answer in listOf(json(500, ""), json(200, "[]"))) {
            assertFailsWith<AltinnException>(answer.toString()) { client { answer }.getAccessList("kv-a") }
        }
    }
}
