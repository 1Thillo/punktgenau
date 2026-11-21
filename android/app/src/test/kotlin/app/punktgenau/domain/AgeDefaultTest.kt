package app.punktgenau.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeDefaultTest {
    @Test fun gespeicherteWerteWerdenGelesen() {
        assertEquals(AgeGroup.UNDER_21, parseAgeGroup("UNDER_21"))
        assertEquals(AgeGroup.UNDER_16, parseAgeGroup("UNDER_16"))
        assertEquals(AgeGroup.ADULT, parseAgeGroup("ADULT"))
    }

    @Test fun fehlendOderUnbekanntIstErwachsen() {
        assertEquals(AgeGroup.ADULT, parseAgeGroup(null))
        assertEquals(AgeGroup.ADULT, parseAgeGroup(""))
        assertEquals(AgeGroup.ADULT, parseAgeGroup("UNDER_99"))
    }

    @Test fun jedeKlasseHatEinenAnzeigenamen() {
        assertEquals("Erwachsen", AgeGroup.ADULT.label)
        assertEquals("unter 21", AgeGroup.UNDER_21.label)
        assertEquals("unter 16", AgeGroup.UNDER_16.label)
    }
}
