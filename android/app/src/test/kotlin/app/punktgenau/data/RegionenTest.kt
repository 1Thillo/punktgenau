package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionenTest {
    @Test
    fun rttvrRegionenMatchTheWebsite() {
        // Per Klick auf der Website geprüft: Oberbereich und Koblenz/Neuwied.
        assertEquals("Rheinland-Rheinhessen", Regionen.name("RTTVR", "DE.SW.R7.12"))
        assertEquals("Koblenz/Neuwied", Regionen.name("RTTVR", "DE.SW.R7.12.01"))
        assertEquals(13, Regionen.fuer("RTTVR").size)
    }

    @Test
    fun everyAssociationHasItsOwnList() {
        assertTrue(Regionen.fuer("BaTTV").isNotEmpty())
        assertEquals("Bruchsal", Regionen.name("BaTTV", "DE.SW.R5.01.01"))
    }

    @Test
    fun unknownCodeFallsBackToAllRegions() {
        assertEquals("Alle Regionen", Regionen.name("RTTVR", null))
        assertEquals("Alle Regionen", Regionen.name("RTTVR", "DE.XX.UNKNOWN"))
        assertEquals(emptyList<Pair<String, String>>(), Regionen.fuer("UNKNOWN"))
    }
}
