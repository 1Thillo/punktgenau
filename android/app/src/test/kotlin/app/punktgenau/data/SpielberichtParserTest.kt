package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpielberichtParserTest {
    /** Spalten wie auf der Website: Rang, Heim, Gast, S1 bis S5, Sätze, Gesamt. */
    private val html = """
        <table>
          <thead><tr><th></th><th>Heim</th><th>Gast</th><th>S1</th><th>S2</th><th>S3</th><th>S4</th><th>S5</th><th>Sätze</th><th>Gesamt</th></tr></thead>
          <tbody>
          <tr><td>D1-D1</td><td><a>Jakob Seul</a> /<br/> <a>Moritz Münch</a></td><td>Oleksandr Rudenko /<br/>Jonathan Schäfer</td><td>11:5</td><td>11:8</td><td>9:11</td><td>11:6</td><td></td><td>3:1</td><td>1:0</td></tr>
          <tr><td>1-2</td><td>Jakob Haag</td><td>Amael&nbsp;Morez</td><td>5:11</td><td>7:11</td><td>11:9</td><td>4:11</td><td></td><td>1:3</td><td>0:1</td></tr>
          </tbody>
        </table>
    """.trimIndent()

    @Test
    fun parsesDoublesAndSingles() {
        val games = SpielberichtParser.games(html)
        assertEquals(2, games.size)
        assertEquals(listOf("Jakob Seul", "Moritz Münch"), games[0].home)
        assertEquals(listOf("Oleksandr Rudenko", "Jonathan Schäfer"), games[0].away)
        assertEquals("3:1", games[0].sets)
        assertEquals("1:0", games[0].total)
        assertEquals(listOf("Amael Morez"), games[1].away)
    }

    @Test
    fun keepsSetScoresInOrderAndSkipsEmptyOnes() {
        val games = SpielberichtParser.games(html)
        assertEquals(listOf("11:5", "11:8", "9:11", "11:6"), games[0].setScores)
        assertEquals(listOf("5:11", "7:11", "11:9", "4:11"), games[1].setScores)
    }

    @Test
    fun headerRowIsSkipped() {
        val header = "<tr><th>Rang</th><th>Heim</th><th>Gast</th><th>S1</th><th>S2</th><th>S3</th><th>S4</th><th>S5</th><th>Sätze</th><th>Gesamt</th></tr>"
        assertEquals(0, SpielberichtParser.games(header).size)
    }
}
