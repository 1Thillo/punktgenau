package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class SpieltagKalenderTest {
    private val now = LocalDateTime.of(2026, 11, 1, 12, 0)
    private val stamp = Instant.parse("2026-11-01T11:00:00Z")

    private val eigene = TeamInfo("1", "Eigene", "Bezirksliga", "2026/27", "42", "RTTVR")

    private fun match(
        team: TeamInfo = eigene,
        date: String? = "2026-11-14T19:00:00",
        home: Boolean? = true,
        meetingId: String? = null,
        league: String? = null,
    ) = SpieltagMatch(
        team,
        Appointment(
            date = date,
            opponentTeamId = "9",
            opponentName = "Gegner",
            home = home,
            meetingId = meetingId,
            leagueId = league,
        ),
    )

    @Test
    fun startTimeIsGermanLocalTimeConvertedToUtc() {
        // 19:00 Ortszeit im November (MEZ, UTC+1) ist 18:00 UTC.
        val ics = SpieltagKalender.ics(listOf(SpieltagKalender.termin(match(), now)!!), stamp)
        assertTrue(ics.contains("DTSTART:20261114T180000Z\r\n"))
        assertTrue(ics.contains("DTEND:20261114T200000Z\r\n"))
    }

    @Test
    fun calendarUsesCrlfAndWrapsAroundVevent() {
        val ics = SpieltagKalender.ics(listOf(SpieltagKalender.termin(match(), now)!!), stamp)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertEquals(1, Regex("END:VEVENT").findAll(ics).count())
        assertFalse(ics.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun emptyListGivesCalendarWithoutEvents() {
        val ics = SpieltagKalender.ics(emptyList(), stamp)
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }

    @Test
    fun homeGameNamesOwnTeamFirst() {
        val t = SpieltagKalender.termin(match(home = true), now)!!
        assertEquals("Eigene", t.home)
        assertEquals("Gegner", t.away)
        assertTrue(SpieltagKalender.ics(listOf(t), stamp).contains("SUMMARY:Eigene gegen Gegner\r\n"))
    }

    @Test
    fun awayGameNamesOpponentFirst() {
        val t = SpieltagKalender.termin(match(home = false), now)!!
        assertEquals("Gegner", t.home)
        assertEquals("Eigene", t.away)
    }

    @Test
    fun leagueAndReportLinkAreInDescriptionAndUrl() {
        val t = SpieltagKalender.termin(match(meetingId = "7"), now)!!
        assertEquals("Bezirksliga", t.league)
        assertTrue(t.url!!.endsWith("/spielbericht/7/x"))
        // Lange Zeilen werden umgebrochen (Fortsetzung mit Leerzeichen); für die Prüfung wieder zusammensetzen.
        val ics = SpieltagKalender.ics(listOf(t), stamp).replace("\r\n ", "")
        assertTrue(ics.contains("CATEGORIES:Bezirksliga\r\n"))
        assertTrue(ics.contains("URL:https://www.mytischtennis.de/click-tt/RTTVR/2026--27/ligen/x/gruppe/42/spielbericht/7/x\r\n"))
        assertTrue(ics.contains("DESCRIPTION:Liga: Bezirksliga\\nSpielbericht: https://"))
    }

    @Test
    fun withoutMeetingIdThereIsNoLink() {
        val t = SpieltagKalender.termin(match(meetingId = null), now)!!
        assertNull(t.url)
        assertFalse(SpieltagKalender.ics(listOf(t), stamp).contains("URL:"))
    }

    @Test
    fun matchWithoutTimeIsSkipped() {
        assertNull(SpieltagKalender.termin(match(date = null), now))
        assertNull(SpieltagKalender.termin(match(date = "2026-11-14"), now))
    }

    @Test
    fun uidIsStableAcrossExports() {
        val a = SpieltagKalender.termin(match(meetingId = "7"), now)!!
        val b = SpieltagKalender.termin(match(meetingId = "7"), now)!!
        assertEquals(a.uid, b.uid)
        assertEquals("1-7-9@punktgenau", a.uid)
    }

    @Test
    fun textIsEscapedPerRfc5545() {
        assertEquals("A\\, B\\; C\\\\D\\nE", SpieltagKalender.escape("A, B; C\\D\nE"))
    }

    @Test
    fun longLinesAreFoldedAtMost75OctetsPerLine() {
        val long = "SUMMARY:" + "Müller-Ünterhaching-Ähre ".repeat(10)
        val parts = SpieltagKalender.fold(long)
        assertTrue(parts.size > 1)
        parts.forEachIndexed { i, p ->
            assertTrue("Zeile $i hat ${p.toByteArray().size} Oktette", p.toByteArray().size <= 75)
            if (i > 0) assertTrue(p.startsWith(" "))
        }
        // Zusammengesetzt ergibt sich wieder die ursprüngliche Zeile.
        assertEquals(long, parts[0] + parts.drop(1).joinToString("") { it.substring(1) })
    }
}
