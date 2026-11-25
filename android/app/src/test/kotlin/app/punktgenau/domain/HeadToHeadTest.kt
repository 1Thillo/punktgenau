package app.punktgenau.domain

import app.punktgenau.data.GameLine
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadToHeadTest {
    private fun game(sets: String, home: List<String?>, away: List<String?>) = GameLine(
        rank = "1", home = home.map { it ?: "x" }, away = away.map { it ?: "x" },
        sets = sets, total = "", setScores = emptyList(),
        homeIds = home, awayIds = away,
    )

    @Test
    fun countsOnlyGamesWithBothOnOpposingSides() {
        val games = listOf(
            game("3:1", home = listOf("A"), away = listOf("B")),   // A auf Heim, Heim gewinnt -> A gewinnt
            game("1:3", home = listOf("A"), away = listOf("B")),   // A auf Heim, Gast gewinnt -> B gewinnt
            game("3:0", home = listOf("B"), away = listOf("A")),   // B auf Heim, Heim gewinnt -> B gewinnt
            game("3:2", home = listOf("A"), away = listOf("C")),   // B fehlt -> nicht gezählt
            game("3:1", home = listOf("A", "B"), away = listOf("C")), // gleiche Seite -> nicht gezählt
        )
        assertEquals(HeadToHead(lines = 3, aWins = 1, bWins = 2), HeadToHeadMath.count(games, "A", "B"))
    }

    @Test
    fun skipsOpenOrUnreadableSetScores() {
        val games = listOf(
            game("", home = listOf("A"), away = listOf("B")),
            game("2:2", home = listOf("A"), away = listOf("B")),
        )
        assertEquals(HeadToHead.NONE, HeadToHeadMath.count(games, "A", "B"))
    }

    @Test
    fun missingIdsAreIgnored() {
        val games = listOf(game("3:1", home = listOf(null), away = listOf("B")))
        assertEquals(HeadToHead.NONE, HeadToHeadMath.count(games, "A", "B"))
    }

    @Test
    fun samePlayerTwiceGivesNothing() {
        val games = listOf(game("3:1", home = listOf("A"), away = listOf("B")))
        assertEquals(HeadToHead.NONE, HeadToHeadMath.count(games, "A", "A"))
    }
}
