package no.kartverket.altinnmanagement.client

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class AltinnEnvironmentTest {

    @Test
    fun `each environment is Altinn and Maskinporten in that environment`() {
        val expected = mapOf(
            AltinnEnvironment.TT02 to listOf("https://platform.tt02.altinn.no", "https://test.maskinporten.no/token"),
            AltinnEnvironment.PROD to listOf("https://platform.altinn.no", "https://maskinporten.no/token"),
        )

        assertEquals(AltinnEnvironment.entries.toSet(), expected.keys)
        for ((environment, urls) in expected) {
            assertEquals(urls.map(::URI), listOf(environment.platformBaseUrl, environment.maskinportenTokenUrl), environment.name)
        }
    }
}
