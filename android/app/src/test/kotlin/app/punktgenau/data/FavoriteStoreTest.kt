package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteStoreTest {
    private class MemoryStorage : StringSetStorage {
        var values: Set<String> = emptySet()
        override fun get(): Set<String> = values
        override fun put(values: Set<String>) {
            this.values = values
        }
    }

    private val storage = MemoryStorage()
    private val store = FavoriteStore(storage)
    private val anna = PlayerLink("NU100", "Anna Berg", club = "TTC Musterstadt", clubNr = "123")
    private val bernd = PlayerLink("NU200", "Bernd Adler")

    @Test fun leer_ohneEintraege() {
        assertTrue(store.load().isEmpty())
        assertFalse(store.contains("NU100"))
    }

    @Test fun hinzufuegen_undWiederLesen() {
        store.add(anna)
        assertEquals(listOf(anna), store.load())
        assertTrue(store.contains("NU100"))
    }

    @Test fun sortiertNachNamen() {
        store.add(anna)
        store.add(bernd)
        assertEquals(listOf("Anna Berg", "Bernd Adler"), store.load().map { it.name })
    }

    @Test fun gleicheKennung_ersetztEintrag() {
        store.add(anna)
        store.add(anna.copy(name = "Anna Berg-Neu"))
        assertEquals(listOf("Anna Berg-Neu"), store.load().map { it.name })
    }

    @Test fun entfernen() {
        store.add(anna)
        store.add(bernd)
        store.remove("NU100")
        assertEquals(listOf(bernd), store.load())
    }

    @Test fun umschalten_liefertNeuenZustand() {
        assertTrue(store.toggle(anna))
        assertTrue(store.contains("NU100"))
        assertFalse(store.toggle(anna))
        assertFalse(store.contains("NU100"))
    }

    @Test fun mannschaftWirdNichtGespeichert() {
        val team = TeamRef(GroupRef("RTTVR", "25/26", "1", "Kreisliga"), "9", "TTC Musterstadt II")
        store.add(anna.copy(team = team))
        assertNull(store.load().single().team)
    }

    @Test fun trennzeichenImNamen_werdenErsetzt() {
        store.add(PlayerLink("NU300", "Carla\tDorn\nX", club = "Verein\tA"))
        val p = store.load().single()
        assertEquals("Carla Dorn X", p.name)
        assertEquals("Verein A", p.club)
    }

    @Test fun kaputteEintraege_werdenIgnoriert() {
        storage.values = setOf("", "\tOhne Kennung", "NurKennung", FavoriteStore.encode(bernd))
        assertEquals(listOf(bernd), store.load())
    }
}
