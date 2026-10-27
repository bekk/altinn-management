package no.kartverket.altinnmanagement.restserver

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    @Test
    fun `the liveness probe answers 200`() = testServer {
        assertEquals(HttpStatusCode.OK, client.get("/health/live").status)
    }
}
