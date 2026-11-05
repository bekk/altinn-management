package no.kartverket.altinnmanagement.restserver

import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingRequest
import no.kartverket.altinnmanagement.client.support.response
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ExternalFilteringTest {

    private val sent = mutableListOf<OutgoingRequest>()
    private val allowed = """{"result":{"scopes":{"exact":[],"prefix":[]},"resources":{"exact":["res-exact"],"prefix":[]}}}"""
    private var answer = response(200, "application/json", allowed)

    private val filtering = ExternalFiltering(
        ExternalFilteringClient(
            URI("https://filtering.test/altinn"),
            OutgoingHttpClient { request ->
                sent += request
                answer
            },
        ),
        cacheDuration = Duration.ofMinutes(1),
    )

    @Test
    fun `asks the filtering service once per caller while the answer is remembered`() = runBlocking {
        filtering.filterFor("caller")
        filtering.filterFor("caller")
        assertEquals(1, sent.size)

        filtering.filterFor("other caller")
        assertEquals(2, sent.size)
    }

    @Test
    fun `does not remember an answer that failed`() = runBlocking {
        answer = response(503, "text/plain", "unavailable")
        assertFailsWith<ExternalFilteringException> { filtering.filterFor("caller") }

        answer = response(200, "application/json", allowed)
        assertEquals(listOf("res-exact"), filtering.filterFor("caller")?.resources?.exact)
        assertEquals(2, sent.size)
    }

    @Test
    fun `gives no filter when filtering is off`() = runBlocking {
        assertFalse(noFiltering.isOn)
        assertNull(noFiltering.filterFor(null))
    }
}
