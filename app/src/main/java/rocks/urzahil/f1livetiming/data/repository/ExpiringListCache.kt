package rocks.urzahil.f1livetiming.data.repository

/**
 * Small in-memory cache for list responses. Empty lists are deliberately not cached because
 * timing/results endpoints can briefly return empty data while a session is still initializing.
 */
internal class ExpiringListCache<K, V>(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: List<V>, val storedAt: Long)
    private val entries = mutableMapOf<K, Entry<V>>()

    operator fun get(key: K): List<V>? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    operator fun set(key: K, value: List<V>) {
        // Empty replies are treated as transient/incomplete and must not evict a good value.
        if (value.isNotEmpty()) {
            entries[key] = Entry(value.toList(), clock())
        }
    }

    fun invalidate(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L
    }
}
