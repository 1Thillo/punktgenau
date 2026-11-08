package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {
    @Test
    fun liefertEintragInnerhalbDerZeit() {
        var t = 0L
        val cache = TtlCache<String, Int>(1_000L) { t }
        cache.put("a", 7)
        t = 1_000L
        assertEquals(7, cache.get("a"))
    }

    @Test
    fun verfälltNachDerZeit() {
        var t = 0L
        val cache = TtlCache<String, Int>(1_000L) { t }
        cache.put("a", 7)
        t = 1_001L
        assertNull(cache.get("a"))
    }

    @Test
    fun unbekannterSchluesselIstLeer() {
        val cache = TtlCache<String, Int>(1_000L)
        assertNull(cache.get("x"))
    }

    @Test
    fun clearLeertAlles() {
        val cache = TtlCache<String, Int>(1_000L)
        cache.put("a", 1)
        cache.clear()
        assertNull(cache.get("a"))
    }
}
