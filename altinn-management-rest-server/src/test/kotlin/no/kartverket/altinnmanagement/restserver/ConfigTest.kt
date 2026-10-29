package no.kartverket.altinnmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import no.kartverket.altinnmanagement.client.AltinnEnvironment
import no.kartverket.altinnmanagement.client.support.testJwk
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ConfigTest {

    private val required = arrayOf(
        "server.port" to "9090",
        "altinn.environment" to "TT02",
        "maskinporten.clientId" to "my-client",
        "maskinporten.clientJwk" to testJwk,
    )

    @Test
    fun `the port, the Altinn environment and the Maskinporten client are read from their keys`() {
        val config = AppConfig.load(MapApplicationConfig(*required))

        assertEquals(9090, config.port)
        assertEquals(AltinnEnvironmentSetting.TT02, config.altinnEnvironment)
        assertEquals("my-client", config.maskinportenClientId)
    }

    @Test
    fun `a key that is not an RSA private key names the setting, but not the key`() {
        val secret = """{"kty":"oct","k":"c2VjcmV0LXZhbHVl"}"""
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("maskinporten.clientJwk" to secret).maskinportenKey("maskinporten.clientJwk")
        }

        assertContains(e.message!!, "maskinporten.clientJwk")
        assertFalse("c2VjcmV0LXZhbHVl" in e.message!!)
    }

    @Test
    fun `the Maskinporten client id is required`() {
        val e = assertFailsWith<IllegalStateException> {
            AppConfig.load(MapApplicationConfig(*required.filterNot { it.first == "maskinporten.clientId" }.toTypedArray()))
        }

        assertContains(e.message!!, "maskinporten.clientId")
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
        assertEquals(true, AppConfig.load(MapApplicationConfig(*required)).accessLog)
        assertEquals(false, AppConfig.load(MapApplicationConfig(*required, "accessLog.enabled" to "false")).accessLog)
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
