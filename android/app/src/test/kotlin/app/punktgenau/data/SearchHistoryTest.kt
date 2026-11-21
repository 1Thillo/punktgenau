package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    private class Memory : TextListStorage {
        var values: List<String> = emptyList()
        override fun get(): List<String> = values
        override fun put(values: List<String>) {
            this.values = values
        }
    }

    private val mem = Memory()
    private val history = SearchHistory(mem)

    @Test fun leerAmAnfang() {
        assertEquals(emptyList<String>(), history.load())
    }

    @Test fun neueSuchenStehenOben() {
        history.add("Schmitt")
        history.add("Jakobs")
        assertEquals(listOf("Jakobs", "Schmitt"), history.load())
    }

    @Test fun gleicheSucheOhneBeachtungGrossKleinRuecktNachOben() {
        history.add("Schmitt")
        history.add("Jakobs")
        history.add("schmitt")
        assertEquals(listOf("schmitt", "Jakobs"), history.load())
    }

    @Test fun leerzeichenWerdenGetrimmt_leereSuchenFallenWeg() {
        history.add("  Gilles  ")
        history.add("   ")
        history.add("")
        assertEquals(listOf("Gilles"), history.load())
    }

    @Test fun begrenztAufZehn() {
        (1..12).forEach { history.add("Name$it") }
        val h = history.load()
        assertEquals(SearchHistory.MAX, h.size)
        assertEquals("Name12", h.first())
        assertEquals("Name3", h.last())
    }

    @Test fun leeren() {
        history.add("Schmitt")
        history.clear()
        assertEquals(emptyList<String>(), history.load())
    }

    @Test fun zeilenumbruchImTextWirdErsetzt() {
        history.add("Anna\nBerg")
        assertEquals(listOf("Anna Berg"), history.load())
    }
}
