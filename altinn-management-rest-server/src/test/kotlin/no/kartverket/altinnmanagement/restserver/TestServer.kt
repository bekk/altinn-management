package no.kartverket.altinnmanagement.restserver

import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

internal fun testServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module() }
    block()
}
