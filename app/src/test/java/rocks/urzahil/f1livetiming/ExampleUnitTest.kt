package rocks.urzahil.f1livetiming

import rocks.urzahil.f1livetiming.data.repository.ExpiringListCache
import rocks.urzahil.f1livetiming.ui.viewmodel.LiveTimingRefreshPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun refreshPolicyUsesSlowerPollingWithoutAnActiveSession() {
        val policy = LiveTimingRefreshPolicy(activePollIntervalMillis = 5_000L, idlePollIntervalMillis = 60_000L)
        assertEquals(5_000L, policy.pollIntervalMillis(sessionIsLive = true))
        assertEquals(60_000L, policy.pollIntervalMillis(sessionIsLive = false))
    }

    @Test
    fun emptyResultsAreNotCached() {
        var now = 100L
        val cache = ExpiringListCache<String, String>(ttlMillis = 1_000L, clock = { now })

        cache["session"] = emptyList()

        assertNull(cache["session"])
    }

    @Test
    fun nonEmptyResultsExpireAfterTtl() {
        var now = 100L
        val cache = ExpiringListCache<String, String>(ttlMillis = 1_000L, clock = { now })
        cache["session"] = listOf("VER", "NOR")

        assertEquals(listOf("VER", "NOR"), cache["session"])
        now += 999L
        assertEquals(listOf("VER", "NOR"), cache["session"])
        now += 1L
        assertNull(cache["session"])
    }

    @Test
    fun emptyRefreshDoesNotOverwritePreviouslyCachedResult() {
        var now = 100L
        val cache = ExpiringListCache<String, String>(ttlMillis = 1_000L, clock = { now })
        cache["session"] = listOf("VER")
        cache["session"] = emptyList()

        assertEquals(listOf("VER"), cache["session"])
    }

    @Test
    fun entriesAreIsolatedBySessionKey() {
        val cache = ExpiringListCache<String, String>()
        cache["2026:1"] = listOf("VER")
        cache["2025:1"] = listOf("HAM")

        assertEquals(listOf("VER"), cache["2026:1"])
        assertEquals(listOf("HAM"), cache["2025:1"])
    }
}
