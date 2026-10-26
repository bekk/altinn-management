package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import no.kartverket.altinnmanagement.client.AltinnEnvironment
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    @Test
    fun `the port and the Altinn environment are read from their keys`() {
        val config = AppConfig.load(MapApplicationConfig("server.port" to "9090", "altinn.environment" to "TT02"))

        assertEquals(9090, config.port)
        assertEquals(AltinnEnvironment.TT02, config.altinnEnvironment)
    }

    @Test
    fun `a misspelled environment names the valid values`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("altinn.environment" to "tt02").enum<AltinnEnvironment>("altinn.environment")
        }

        assertContains(e.message!!, "altinn.environment")
        assertContains(e.message!!, "TT02, PROD")
    }

    @Test
    fun `a port that is not a port number names the setting`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("server.port" to "80a").port("server.port") }

        assertContains(e.message!!, "server.port")
    }
}
