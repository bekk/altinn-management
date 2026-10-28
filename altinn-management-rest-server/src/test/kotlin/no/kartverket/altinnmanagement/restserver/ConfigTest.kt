package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import no.kartverket.altinnmanagement.client.AltinnEnvironment
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    @Test
    fun `the port and the Altinn environment are read from their keys`() {
        val config = AppConfig.load(MapApplicationConfig("server.port" to "9090", "altinn.environment" to "TT02"))

        assertEquals(9090, config.port)
        assertEquals(AltinnEnvironmentSetting.TT02, config.altinnEnvironment)
    }

    @Test
    fun `a misspelled environment names the valid values`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("altinn.environment" to "tt02").enum<AltinnEnvironmentSetting>("altinn.environment")
        }

        assertContains(e.message!!, "altinn.environment")
        assertContains(e.message!!, "TT02, PROD, LOCAL")
    }

    @Test
    fun `a port that is not a port number names the setting`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("server.port" to "80a").port("server.port") }

        assertContains(e.message!!, "server.port")
    }

    @Test
    fun `the access log is on unless switched off`() {
        val required = arrayOf("server.port" to "8080", "altinn.environment" to "TT02")

        assertEquals(true, AppConfig.load(MapApplicationConfig(*required)).accessLog)
        assertEquals(false, AppConfig.load(MapApplicationConfig(*required, "accessLog.enabled" to "false")).accessLog)
    }

    @Test
    fun `external filtering is off unless switched on, and then needs an http or https URL`() {
        val required = arrayOf("server.port" to "8080", "altinn.environment" to "LOCAL")
        fun load(vararg settings: Pair<String, String>) = AppConfig.load(MapApplicationConfig(*required, *settings))

        assertEquals(null, load("externalFiltering.url" to "http://localhost:8090/filtering").externalFilteringUrl)
        assertEquals(
            URI("http://localhost:8090/filtering"),
            load("externalFiltering.enabled" to "true", "externalFiltering.url" to "http://localhost:8090/filtering").externalFilteringUrl,
        )
        for (url in listOf(null, "localhost:8090/filtering", "ftp://opa/filtering", "http://opa/filtering?x=1")) {
            val settings = listOfNotNull("externalFiltering.enabled" to "true", url?.let { "externalFiltering.url" to it })
            val e = assertFailsWith<IllegalStateException>("$url") { load(*settings.toTypedArray()) }

            assertContains(e.message!!, "externalFiltering.url")
            assertContains(e.message!!, "(see .env.example)")
        }
    }

    @Test
    fun `a flag must be true or false`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("flag" to "yes").boolean("flag", default = true) }

        assertContains(e.message!!, "must be true or false")
    }

    @Test
    fun `refuses a logback config path that is not a file`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("logback.configFile" to "/no/such/logback.xml").file("logback.configFile")
        }

        assertContains(e.message!!, "/no/such/logback.xml")
    }

    @Test
    fun `LOCAL is the mock API, and the other environments are the client's`() {
        assertEquals(URI("http://localhost:8090"), AltinnEnvironmentSetting.LOCAL.platformBaseUrl)
        assertEquals(AltinnEnvironment.TT02.platformBaseUrl, AltinnEnvironmentSetting.TT02.platformBaseUrl)
        assertEquals(AltinnEnvironment.PROD.maskinportenTokenUrl, AltinnEnvironmentSetting.PROD.maskinportenTokenUrl)
    }
}
