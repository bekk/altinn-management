package no.kartverket.altinnmanagement.client.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AltinnManagementExceptionTest {

    @Test
    fun `appends a short body to the message`() {
        assertEquals("boom: access denied", AltinnException("boom", responseBody = "access denied").message)
    }

    @Test
    fun `leaves the message alone when there is no body`() {
        assertEquals("boom", AltinnException("boom").message)
        assertEquals("boom", AltinnException("boom", responseBody = "").message)
    }

    @Test
    fun `abbreviates a long body in the message but keeps it whole on the exception`() {
        val body = "x".repeat(600)

        val e = AltinnException("boom", responseBody = body)

        assertTrue(e.message!!.startsWith("boom: " + "x".repeat(500)))
        assertTrue(e.message!!.endsWith("… (600 characters in total)"))
        assertEquals(body, e.responseBody, "the full body stays available for callers that want it")
    }
}
