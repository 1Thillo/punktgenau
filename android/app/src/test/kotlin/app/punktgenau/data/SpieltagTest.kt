package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalDateTime

class SpieltagTest {
    private val team = TeamInfo("1", "Eigene", "Liga")

    private fun match(date: String?, home: Boolean = true): SpieltagMatch =
        SpieltagMatch(team, Appointment(date, "2", "Gegner", home, null))

    @Test
    fun daysAreDistinctAndSorted() {
        val matches = listOf(
            match("2026-11-14T19:00:00"),
            match("2026-11-08T19:30:00"),
            match("2026-11-08T10:00:00", home = false),
            match(null),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), Spieltag.days(matches))
    }

    @Test
    fun startIndexPicksFirstDayFromToday() {
        val days = listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15))
        assertEquals(1, Spieltag.startIndex(days, LocalDate.of(2026, 11, 7)))
        assertEquals(1, Spieltag.startIndex(days, LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun startIndexFallsBackToLastDayWhenAllPassed() {
        val days = listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8))
        assertEquals(1, Spieltag.startIndex(days, LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun startIndexIsMinusOneWithoutDays() {
        assertEquals(-1, Spieltag.startIndex(emptyList(), LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun reportUrlUsesSeasonWithDoubleDashAndBothIds() {
        assertEquals(
            "https://www.mytischtennis.de/click-tt/RTTVR/26--27/ligen/x/gruppe/518414/spielbericht/15898549/x",
            Spieltag.reportUrl("RTTVR", "26/27", "518414", "15898549"),
        )
    }

    /** Die Website nennt Weltzeit; angezeigt wird deutsche Ortszeit (Winter +1, Sommer +2 Stunden). */
    @Test
    fun kickoffIsShownInGermanLocalTime() {
        assertEquals("20:00", match("2026-11-13T19:00:00.000+00:00").time)
        assertEquals(LocalDate.of(2026, 11, 13), match("2026-11-13T19:00:00.000+00:00").day)
        assertEquals("19:30", match("2026-09-04T17:30:00.000+00:00").time)
    }

    private fun appointment(state: String?, complete: Boolean = false, live: Boolean = false, result: String? = null) =
        Appointment(
            "2026-11-13T19:00:00.000+00:00", "3142298", "TTV Andernach III", home = false, result = result,
            meetingId = "15898557", leagueId = "518414", ownName = "TTG Nettetal II", live = live, state = state, complete = complete,
            ownClubNr = "002014", opponentClubNr = "004314",
        )

    /** Der Fall vom 13.11.2026: 20:31 Uhr, Anstoß 20:00 Uhr, noch kein Ergebnis, keine Live-Erfassung. */
    @Test
    fun matchWithoutLiveEntryCountsAsRunningAfterKickoff() {
        val a = appointment(state = "scheduled")
        assertTrue(Spieltag.isRunning(a, LocalDateTime.of(2026, 11, 13, 20, 31)))
        assertFalse(Spieltag.isRunning(a, LocalDateTime.of(2026, 11, 13, 19, 59)))
        assertFalse(Spieltag.isRunning(a, LocalDateTime.of(2026, 11, 14, 0, 0)))
    }

    @Test
    fun finishedMatchIsNotRunningAndLiveFlagAlwaysIs() {
        val evening = LocalDateTime.of(2026, 11, 13, 21, 50)
        assertFalse(Spieltag.isRunning(appointment(state = "done", complete = true, result = "8:2"), evening))
        assertTrue(Spieltag.isRunning(appointment(state = "scheduled", live = true), LocalDateTime.of(2026, 11, 13, 12, 0)))
    }

    /** Der Spielbericht ist ohne Ergebnis erreichbar; Heim und Gast stehen richtig herum. */
    @Test
    fun reportLinkExistsWithoutResult() {
        val group = GroupRef("RTTVR", "26/27", "518414", "2. Kreisklasse Gruppe B")
        val own = TeamRef(group, "3081442", "TTG Nettetal II")
        val link = Spieltag.reportLink(own, appointment(state = "scheduled"), LocalDateTime.of(2026, 11, 13, 20, 31))!!
        assertEquals("https://www.mytischtennis.de/click-tt/RTTVR/26--27/ligen/x/gruppe/518414/spielbericht/15898557/x", link.url)
        assertTrue(link.running)
        assertEquals("TTV Andernach III gegen TTG Nettetal II", link.title)
        assertEquals("3142298", link.home!!.teamId)
        assertEquals("004314", link.home!!.clubNr)
        assertEquals("002014", link.away!!.clubNr)
        assertNull(Spieltag.reportLink(own, appointment(state = "scheduled").copy(meetingId = null), LocalDateTime.of(2026, 11, 13, 20, 31)))
    }

    /** Verlauf → Spiel: der Verlauf nennt nur Datum und Paarung, der Spielplan die Kennung. */
    @Test
    fun historyEventIsMatchedToScheduleByDayAndPairing() {
        assertEquals("TTV Andernach III" to "TTG Nettetal II", Spieltag.eventTeams("2. KK-Erwachsene | TTV Andernach III : TTG Nettetal II"))
        assertNull(Spieltag.eventTeams("Quartals-Nachwuchs-Spielstaerkezuwachs"))
        val other = appointment(state = "done").copy(date = "2026-11-06T19:00:00.000+00:00", meetingId = "1")
        val schedule = listOf(other, appointment(state = "done", complete = true, result = "8:2"))
        val day = LocalDate.of(2026, 11, 13)
        assertEquals("15898557", Spieltag.matchEvent(day, "TTV Andernach III", "TTG  Nettetal II", schedule)!!.meetingId)
        assertNull(Spieltag.matchEvent(day, "TTG Nettetal II", "TTV Andernach III", schedule))
        assertNull(Spieltag.matchEvent(LocalDate.of(2026, 11, 12), "TTV Andernach III", "TTG Nettetal II", schedule))
    }

    @Test
    fun matchDayAndTimeFromIsoDate() {
        val m = match("2026-11-08T19:30:00")
        assertEquals(LocalDate.of(2026, 11, 8), m.day)
        assertEquals("19:30", m.time)
        assertNull(match(null).day)
    }
}
