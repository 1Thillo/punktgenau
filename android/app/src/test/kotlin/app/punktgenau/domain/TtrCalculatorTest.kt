package app.punktgenau.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtrCalculatorTest {

    private fun change(own: Int, k: Int, vararg games: Pair<Int, Boolean>) =
        TtrCalculator.calculate(own, games.map { Game(it.first, it.second) }, k).change

    // --- Referenzbeispiele der Formel -------------------------------------------------

    @Test fun beideVerloren_gleichStark() = assertEquals(-16, change(1500, 16, 1500 to false, 1500 to false))
    @Test fun beideVerloren_gegnerStaerker() = assertEquals(-4, change(1500, 16, 1700 to false, 1600 to false))
    @Test fun beideVerloren_gegnerSchwaecher() = assertEquals(-28, change(1500, 16, 1300 to false, 1400 to false))
    @Test fun einSiegEineNiederlage() = assertEquals(0, change(1500, 16, 1500 to true, 1500 to false))
    @Test fun beideGewonnen_gegnerStaerker() = assertEquals(28, change(1500, 16, 1700 to true, 1600 to true))
    @Test fun beideVerloren_unter21() = assertEquals(-20, change(1500, 20, 1500 to false, 1500 to false))
    @Test fun unter16UndWenigeEinzel_einSieg() = assertEquals(5, change(1200, 28, 1100 to true))

    // --- Der gemeldete Fehler: zwei Niederlagen duerfen nie 0 ergeben ------------

    @Test fun beideVerloren_nieNull_ueberViele_Gegnerwerte() {
        for (own in listOf(800, 1200, 1500, 1800, 2200)) {
            for (diff in -250..250 step 25) { // realistische Spannweite; darüber rundet 2 x 16 x E auf 0
                val c = change(own, 16, (own + diff) to false, (own + diff) to false)
                assertTrue("own=$own diff=$diff change=$c", c < 0)
            }
        }
    }

    @Test fun beideVerloren_ergebnisHatVorzeichenMinus() {
        val r = TtrCalculator.calculate(1500, listOf(Game(1500, false), Game(1500, false)), 16)
        assertEquals(0, r.actualSum)
        assertEquals(1.0, r.expectedSum, 1e-9)
        assertEquals(1484, r.newRating)
    }

    // --- Rundung ----------------------------------------------------------------

    @Test fun rundung_halbeWerdenAufgerundet() {
        assertEquals(1, TtrCalculator.roundHalfUp(0.5))
        assertEquals(0, TtrCalculator.roundHalfUp(-0.5))
        assertEquals(-1, TtrCalculator.roundHalfUp(-0.51))
        assertEquals(2, TtrCalculator.roundHalfUp(1.5))
        assertEquals(-1, TtrCalculator.roundHalfUp(-1.5))
    }

    @Test fun einEinzelGleichStark_16_gibtPlusMinus8() {
        assertEquals(8, change(1500, 16, 1500 to true))
        assertEquals(-8, change(1500, 16, 1500 to false))
    }

    // --- Sonderfaelle -----------------------------------------------------------

    @Test fun leereListe_keineAenderung() {
        val r = TtrCalculator.calculate(1500, emptyList(), 16)
        assertEquals(0, r.change)
        assertEquals(1500, r.newRating)
    }

    @Test fun nullTtr_keinAbsturz() {
        val r = TtrCalculator.calculate(0, listOf(Game(1500, false)), 16)
        assertEquals(0, r.change) // E ~ 0, Niederlage erwartet
    }

    @Test fun sehrGrosseDifferenz_keinOverflow() {
        assertEquals(16, change(0, 16, 3000 to true))
        assertEquals(-16, change(3000, 16, 0 to false))
        assertEquals(0, change(3000, 16, 0 to true))
    }

    @Test fun gewinnErwartung_symmetrisch() {
        val a = TtrCalculator.winExpectation(1600, 1400)
        val b = TtrCalculator.winExpectation(1400, 1600)
        assertEquals(1.0, a + b, 1e-12)
        assertEquals(0.5, TtrCalculator.winExpectation(1500, 1500), 1e-12)
    }

    @Test fun alleSpieleNutzenDenWertVorDemTermin() {
        // Zwei Siege gegen 1500 bei Eigenwert 1500: nicht nacheinander fortgeschrieben.
        assertEquals(16, change(1500, 16, 1500 to true, 1500 to true))
    }

    // --- Aenderungskonstante ------------------------------------------------------

    @Test fun konstante_alleKombinationen() {
        val expected = mutableSetOf<Int>()
        for (age in AgeGroup.entries) for (a in listOf(false, true)) for (b in listOf(false, true)) {
            val k = TtrCalculator.alterationConstant(AlterationInput(age, a, b))
            val manual = 16 + age.surcharge + (if (a) 4 else 0) + (if (b) 4 else 0)
            assertEquals(manual, k)
            expected += k
        }
        assertEquals(setOf(16, 20, 24, 28, 32), expected) // Höchstwert 16 + 4 x 4 = 32
    }

    @Test fun konstante_grundwert16() = assertEquals(16, TtrCalculator.alterationConstant(AlterationInput()))
    @Test fun konstante_unter21() = assertEquals(20, TtrCalculator.alterationConstant(AlterationInput(AgeGroup.UNDER_21)))
    @Test fun konstante_unter16Plus30Einzel() =
        assertEquals(28, TtrCalculator.alterationConstant(AlterationInput(AgeGroup.UNDER_16, fewerThan30Singles = true)))
    @Test fun konstante_hoechstwert32() =
        assertEquals(32,TtrCalculator.alterationConstant(AlterationInput(AgeGroup.UNDER_16, true, true)))

    @Test fun berechnungMitEingaben_nutztDieKonstante() {
        val r = TtrCalculator.calculate(
            1200, listOf(Game(1100, true)),
            AlterationInput(AgeGroup.UNDER_16, fewerThan30Singles = true),
        )
        assertEquals(28, r.alterationConstant)
        assertEquals(5, r.change)
        assertEquals(1205, r.newRating)
    }
}
