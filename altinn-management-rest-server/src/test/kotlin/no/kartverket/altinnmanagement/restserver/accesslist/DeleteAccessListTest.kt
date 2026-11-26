package no.kartverket.altinnmanagement.restserver.accesslist

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.restserver.assertProblem
import no.kartverket.altinnmanagement.restserver.testServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteAccessListTest {

    private val sent = mutableListOf<OutgoingRequest>()

    @Test
    fun `deletes the list in Altinn and answers with it as it was`() = testServer(altinnAnswering(sent, 200, altinnList("kv-liste"))) {
        val response = client.delete("/api/accesslists/kv-liste")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(accessList("kv-liste"), response.accessList())
        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("$ALTINN_LISTS/kv-liste", request.url.toString())
    }

    @Test
    fun `gives 404 when Kartverket has no list with the id`() = testServer(altinnAnswering(sent, 204)) {
        client.delete("/api/accesslists/kv-finnes-ikke").assertProblem(accessListNotFound)
    }

    @Test
    fun `gives 400 for an id with a colon or capital letters, without asking Altinn`() = testServer(altinnAnswering(sent, 200)) {
        for (id in listOf("kv:liste", "KV-LISTE")) client.delete("/api/accesslists/$id").assertProblem(invalidId("id"))

        assertTrue(sent.isEmpty(), "Altinn was called: $sent")
    }
}
