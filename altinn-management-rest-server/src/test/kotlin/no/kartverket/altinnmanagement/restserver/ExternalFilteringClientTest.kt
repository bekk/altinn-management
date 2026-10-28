package no.kartverket.altinnmanagement.restserver

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.altinnmanagement.client.http.HttpMethod
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.http.OutgoingResponse
import no.kartverket.altinnmanagement.client.support.response
import java.io.IOException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExternalFilteringClientTest {

    private val sent = mutableListOf<OutgoingRequest>()
    private val certificate =
        "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

    private fun filteringAnswering(answer: (OutgoingRequest) -> OutgoingResponse) = ExternalFilteringClient(
        URI("https://filtering.test/altinn"),
        OutgoingHttpClient { request ->
            sent += request
            answer(request)
        },
    )

    private fun json(status: Int, body: String) = response(status, "application/json", body)

    @Serializable
    private data class SentRequest(val input: SentInput)

    @Serializable
    private data class SentInput(val headers: Map<String, String>)

    @Test
    fun `posts the client certificate as input headers and reads scopes and resources from the result`() = runBlocking {
        val filtering = filteringAnswering {
            json(
                200,
                """{"result":{
                    "scopes":{"exact":["kartverk:dokumentbestilling"],"prefix":["kartverk:tilgangsstyring:"]},
                    "resources":{"exact":["res-exact"],"prefix":["res-prefix-"]}
                }}""",
            )
        }

        val filter = filtering.filterFor(certificate)

        assertEquals(
            AccessFilter(
                scopes = Filter(exact = listOf("kartverk:dokumentbestilling"), prefix = listOf("kartverk:tilgangsstyring:")),
                resources = Filter(exact = listOf("res-exact"), prefix = listOf("res-prefix-")),
            ),
            filter,
        )
        val request = sent.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://filtering.test/altinn", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        val sentRequest = Json.decodeFromString(SentRequest.serializer(), request.body!!.decodeToString())
        assertEquals(mapOf("x-forwarded-client-cert" to certificate), sentRequest.input.headers)
    }

    @Test
    fun `ignores fields it does not know`() = runBlocking {
        val filtering = filteringAnswering {
            json(
                200,
                """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":{"exact":[],"prefix":[]},"actions":["read"]},"decision_id":"abc"}""",
            )
        }

        assertEquals(AccessFilter.NOTHING, filtering.filterFor(certificate))
    }

    @Test
    fun `treats a missing result as an undefined document, so no access`() = runBlocking {
        val filtering = filteringAnswering { json(200, "{}") }

        assertEquals(AccessFilter.NOTHING, filtering.filterFor(certificate))
    }

    @Test
    fun `fails when the answer is not scopes and resources, each with exact and prefix`() {
        for (body in listOf(
            """{"result":{"exact":[],"prefix":[]}}""",
            """{"result":{"scopes":{"exact":[],"prefix":[]}}}""",
            """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":{"exact":[]}}}""",
            """{"result":{"scopes":{"exact":[null],"prefix":[]},"resources":{"exact":[],"prefix":[]}}}""",
            """{"result":[]}""",
            "[]",
            "not json",
            "",
        )) {
            val filtering = filteringAnswering { json(200, body) }

            assertFailsWith<ExternalFilteringException>(body) { runBlocking { filtering.filterFor(certificate) } }
        }
    }

    @Test
    fun `fails when a prefix is blank, since it would allow everything`() {
        val filtering = filteringAnswering {
            json(200, """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":{"exact":[],"prefix":["res-prefix-",""]}}}""")
        }

        val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

        assertContains(e.message!!, "must not be blank")
    }

    @Test
    fun `fails when the filtering service answers with an error`() {
        for (status in listOf(400, 403, 404, 500, 503)) {
            val filtering = filteringAnswering { json(status, "{}") }

            val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

            assertContains(e.message!!, "$status")
        }
    }

    @Test
    fun `fails when the filtering service cannot be reached`() {
        val filtering = filteringAnswering { throw IOException("Connection refused") }

        val e = assertFailsWith<ExternalFilteringException> { runBlocking { filtering.filterFor(certificate) } }

        assertContains(e.message!!, "Connection refused")
    }
}
