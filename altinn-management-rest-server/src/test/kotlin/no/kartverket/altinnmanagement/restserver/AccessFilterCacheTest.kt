package no.kartverket.altinnmanagement.restserver

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessFilterCacheTest {

    private var now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    private val filter = AccessFilter(
        scopes = Filter(exact = listOf("kartverk:dokumentbestilling"), prefix = emptyList()),
        resources = Filter(exact = listOf("res-exact"), prefix = emptyList()),
    )

    @Test
    fun `remembers a caller's filter until the duration is over`() {
        val cache = AccessFilterCache(Duration.ofMinutes(1), clock)
        cache.put("caller", filter)

        now += Duration.ofSeconds(59)
        assertEquals(filter, cache.get("caller"))
        now += Duration.ofSeconds(1)
        assertNull(cache.get("caller"))
    }

    @Test
    fun `remembers nothing when the duration is zero`() {
        val cache = AccessFilterCache(Duration.ZERO, clock)
        cache.put("caller", filter)

        assertNull(cache.get("caller"))
    }

    @Test
    fun `keeps at most MAX_ENTRIES callers, dropping the oldest first`() {
        val cache = AccessFilterCache(Duration.ofMinutes(1), clock)
        repeat(AccessFilterCache.MAX_ENTRIES + 1) { cache.put("caller $it", filter) }

        assertNull(cache.get("caller 0"))
        assertEquals(filter, cache.get("caller 1"))
        assertEquals(filter, cache.get("caller ${AccessFilterCache.MAX_ENTRIES}"))
    }
}
