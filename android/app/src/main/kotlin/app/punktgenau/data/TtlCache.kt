package app.punktgenau.data

/**
 * Kleiner Zwischenspeicher mit Verfallszeit. Spart Abrufe bei der Website: gleiche Antworten werden
 * innerhalb von [ttlMillis] nicht noch einmal geholt. Nur erfolgreiche Antworten gehören hinein.
 */
class TtlCache<K, V>(
    private val ttlMillis: Long,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val entries = HashMap<K, Pair<Long, V>>()

    @Synchronized
    fun get(key: K): V? {
        val (at, value) = entries[key] ?: return null
        if (now() - at > ttlMillis) {
            entries.remove(key)
            return null
        }
        return value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = now() to value
    }

    @Synchronized
    fun clear() = entries.clear()
}
