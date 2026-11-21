package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MatchAlertsTest {
    private val now = LocalDateTime.of(2026, 11, 21, 18, 0)
    private val team = TeamInfo("T1", "Erwachsene SG", null, null, null, null, null)

    private fun match(date: String?, result: String? = null, opponent: String = "DJK Mayen") = SpieltagMatch(
        team,
        Appointment(date = date, opponentTeamId = "O1", opponentName = opponent, result = result),
    )

    // Termine im Stil der Serverantwort (Ortszeit mit Offset)
    private val inTwoHours = "2026-11-21T20:00:00+01:00"
    private val inFiveHours = "2026-11-21T23:00:00+01:00"
    private val past = "2026-11-21T16:00:00+01:00"

    @Test fun spielInDenNaechstenDreiStunden_wirdAngekuendigt() {
        val alerts = MatchAlerts.plan(listOf(match(inTwoHours)), now, emptySet())
        assertEquals(1, alerts.size)
        assertEquals("Spiel heute um 20:00", alerts.single().title)
    }

    @Test fun spielSpaeter_wirdNochNichtAngekuendigt() {
        assertTrue(MatchAlerts.plan(listOf(match(inFiveHours)), now, emptySet()).isEmpty())
    }

    @Test fun vergangenesSpiel_wirdNichtAngekuendigt() {
        assertTrue(MatchAlerts.plan(listOf(match(past)), now, emptySet()).isEmpty())
    }

    @Test fun bereitsGemeldetes_wirdNichtWiederholt() {
        val m = match(inTwoHours)
        val sent = setOf("soon|" + MatchAlerts.key(m))
        assertTrue(MatchAlerts.plan(listOf(m), now, sent).isEmpty())
    }

    @Test fun ergebnis_wirdEinmalGemeldet() {
        val m = match(past, result = "6:4")
        val first = MatchAlerts.plan(listOf(m), now, emptySet())
        assertEquals(1, first.size)
        assertEquals("Ergebnis: Erwachsene SG", first.single().title)
        assertTrue(first.single().text.endsWith("6:4"))
        assertTrue(MatchAlerts.plan(listOf(m), now, first.map { it.key }.toSet()).isEmpty())
    }

    @Test fun leeresErgebnis_istKeinErgebnis() {
        assertTrue(MatchAlerts.plan(listOf(match(past, result = " ")), now, emptySet()).isEmpty())
    }

    @Test fun ohneLesbaresDatum_wirdUebersprungen() {
        assertTrue(MatchAlerts.plan(listOf(match(null)), now, emptySet()).isEmpty())
    }

    @Test fun store_merktUndBegrenzt() {
        val mem = object : StringSetStorage {
            var v: Set<String> = emptySet()
            override fun get() = v
            override fun put(values: Set<String>) {
                v = values
            }
        }
        val store = AlertStore(mem)
        store.add(listOf("a", "b"))
        store.add(listOf("c"))
        assertEquals(setOf("a", "b", "c"), store.sent())
        store.add((1..400).map { "k$it" })
        assertEquals(300, store.sent().size)
    }
}
