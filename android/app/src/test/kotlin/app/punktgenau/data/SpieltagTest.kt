package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

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
    fun topAverageUsesTheFourHighestRounded() {
        // 1700, 1650, 1600, 1550 -> 1625; die 1400 bleibt außen vor.
        assertEquals(1625, Spieltag.topAverage(listOf(1400, 1550, 1700, 1600, 1650)))
    }

    @Test
    fun topAverageWithFewerThanFourValues() {
        assertEquals(1601, Spieltag.topAverage(listOf(1600, 1602)))
    }

    @Test
    fun topAverageIsNullWithoutValues() {
        assertNull(Spieltag.topAverage(emptyList()))
    }

    @Test
    fun lineupNamesNimmtDieEigeneSeiteOhneDoppelungen() {
        val games = listOf(
            GameLine("D1-D1", listOf("Moritz Bernd", "Frank Große"), listOf("Achim Jakoby", "Torsten Daun"), "0:3", "0 : 1", emptyList()),
            GameLine("1-2", listOf("Peter Leonard"), listOf("Wolfgang Hoff"), "3:1", "1 : 2", emptyList()),
            GameLine("4-4", listOf("Frank Große"), listOf("Torsten Daun"), "3:0", "4 : 6", emptyList()),
        )
        assertEquals(listOf("Moritz Bernd", "Frank Große", "Peter Leonard"), Spieltag.lineupNames(games, ownHome = true))
        assertEquals(listOf("Achim Jakoby", "Torsten Daun", "Wolfgang Hoff"), Spieltag.lineupNames(games, ownHome = false))
    }

    @Test
    fun reportUrlUsesSeasonWithDoubleDashAndBothIds() {
        assertEquals(
            "https://www.mytischtennis.de/click-tt/RTTVR/26--27/ligen/x/gruppe/518414/spielbericht/15898549/x",
            Spieltag.reportUrl("RTTVR", "26/27", "518414", "15898549"),
        )
    }

    @Test
    fun matchDayAndTimeFromIsoDate() {
        val m = match("2026-11-08T19:30:00")
        assertEquals(LocalDate.of(2026, 11, 8), m.day)
        assertEquals("19:30", m.time)
        assertNull(match(null).day)
    }
}
