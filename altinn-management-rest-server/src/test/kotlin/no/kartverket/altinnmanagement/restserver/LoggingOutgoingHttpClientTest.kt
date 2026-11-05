package no.kartverket.altinnmanagement.restserver

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import no.kartverket.altinnmanagement.client.ResourceId
import no.kartverket.altinnmanagement.client.exception.AltinnException
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import no.kartverket.altinnmanagement.client.support.response
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LoggingOutgoingHttpClientTest {

    private lateinit var appender: ListAppender<ILoggingEvent>

    private val outgoing get() = LoggerFactory.getLogger("outgoing") as Logger

    private val lines get() = appender.list.map { it.formattedMessage }

    @BeforeTest
    fun attachAppender() {
        appender = ListAppender<ILoggingEvent>().apply { start() }
        outgoing.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        outgoing.detachAppender(appender)
    }

    private fun getThrough(altinn: OutgoingHttpClient) = runBlocking {
        testResourceRegistryClient(LoggingOutgoingHttpClient(altinn)).getMaskinportenSchema(ResourceId.of("kv-matrikkel-brukernavn"))
    }

    private val url = "https://platform.tt02.altinn.no/resourceregistry/api/v1/resource/kv-matrikkel-brukernavn"

    @Test
    fun `logs each call before it is sent and its status after, but not its headers or body`() {
        assertFailsWith<AltinnException> {
            getThrough {
                assertEquals("Calling GET $url", lines.last())
                response(500, "text/plain", "secret answer")
            }
        }

        assertEquals(2, lines.size, "$lines")
        assertTrue(lines[1].startsWith("GET $url answered 500 in "), lines[1])
        assertTrue(lines.none { "secret answer" in it || "Accept" in it }, "a header or body was logged: $lines")
    }

    @Test
    fun `logs a call that got no answer, and passes the failure on`() {
        assertFailsWith<AltinnException> {
            getThrough { throw IOException("Connection refused") }
        }

        assertTrue(lines.last().startsWith("GET $url failed after "), lines.last())
        assertContains(lines.last(), "Connection refused")
    }
}
