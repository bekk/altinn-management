package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    @Test
    fun `the port is read from server port`() {
        assertEquals(9090, AppConfig.load(MapApplicationConfig("server.port" to "9090")).port)
    }

    @Test
    fun `a port that is not a port number names the setting`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("server.port" to "80a").port("server.port") }

        assertContains(e.message!!, "server.port")
    }
}
