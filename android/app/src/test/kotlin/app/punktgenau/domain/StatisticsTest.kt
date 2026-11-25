package app.punktgenau.domain

import app.punktgenau.data.HistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatisticsTest {
    private fun meeting(day: Int, won: Int, lost: Int, delta: Int) = HistoryEvent(
        date = LocalDate.of(2026, 11, day), before = 1000, after = 1000 + delta, type = "meeting",
        name = "Liga | A : B", delta = delta, won = won, lost = lost,
    )

    private fun other(day: Int, delta: Int) = HistoryEvent(
        date = LocalDate.of(2026, 11, day), before = 1000, after = 1000 + delta, type = "correction",
        name = "Korrektur", delta = delta,
    )

    @Test
    fun countsMeetingsAndMatches() {
        val s = Statistics.summarize(listOf(
            meeting(1, won = 6, lost = 4, delta = 8),
            meeting(2, won = 3, lost = 7, delta = -12),
            meeting(3, won = 5, lost = 5, delta = 0),
        ))
        assertEquals(3, s.meetings)
        assertEquals(1, s.meetingWins)
        assertEquals(1, s.meetingLosses)
        assertEquals(1, s.meetingDraws)
        assertEquals(14, s.matchesWon)
        assertEquals(16, s.matchesLost)
        assertEquals(47, s.matchWinRate)
    }

    @Test
    fun findsBiggestGainAndLossAcrossAllEvents() {
        val s = Statistics.summarize(listOf(
            meeting(1, 6, 4, 8),
            other(2, 25),
            meeting(3, 2, 8, -12),
            other(4, -30),
        ))
        assertEquals(25, s.biggestGain?.delta)
        assertEquals(-30, s.biggestLoss?.delta)
    }

    @Test
    fun emptyEventsGiveNoRateAndNoExtremes() {
        val s = Statistics.summarize(emptyList())
        assertEquals(0, s.meetings)
        assertNull(s.matchWinRate)
        assertNull(s.biggestGain)
        assertNull(s.biggestLoss)
    }

    @Test
    fun onlyGainsGiveNoLoss() {
        val s = Statistics.summarize(listOf(meeting(1, 6, 4, 8)))
        assertEquals(8, s.biggestGain?.delta)
        assertNull(s.biggestLoss)
    }
}
