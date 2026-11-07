package app.punktgenau.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeamParserTest {
    @Test fun saison() {
        assertEquals("26/27", Season.label(LocalDate.of(2026, 11, 7)))
        assertEquals("26/27", Season.label(LocalDate.of(2026, 7, 1)))
        assertEquals("25/26", Season.label(LocalDate.of(2026, 6, 30)))
        assertEquals("26/27", Season.label(LocalDate.of(2027, 2, 1)))
    }

    @Test fun teams_bevorzugenLaufendeSaison() {
        val body = """{"data":[{"team_id":"1","team_name":"Alt","season":"25/26"},{"team_id":"2","team_name":"Neu","season":"26/27"}]}"""
        assertEquals(listOf("2"), TeamParser.parseTeams(body, "26/27")!!.map { it.id })
    }

    @Test fun teams_ohnePassendeSaison_alle() {
        val body = """{"data":[{"team_id":"1","team_name":"A","season":"25/26"}]}"""
        assertEquals(1, TeamParser.parseTeams(body, "26/27")!!.size)
    }

    @Test fun termine_sortiert() {
        val body = """{"data":[{"date":"2026-12-01T19:00:00+01:00","opponent_team_id":"9","opponent_team_name":"B"},{"date":"2026-11-01T19:00:00+01:00","opponent_team_id":"8","opponent_team_name":"A"}]}"""
        assertEquals(listOf("A", "B"), TeamParser.parseSchedule(body)!!.map { it.opponentName })
    }

    @Test fun spieler_nachRang() {
        val body = """{"data":[{"firstname":"B","lastname":"Zwei","internal_id":"NU2","rank":2},{"firstname":"A","lastname":"Eins","internal_id":"NU1","rank":1}]}"""
        assertEquals(listOf("A Eins", "B Zwei"), TeamParser.parsePlayers(body)!!.map { it.displayName })
    }

    @Test fun unlesbar() {
        assertNull(TeamParser.parseSchedule("<html>"))
        assertNull(TeamParser.parsePlayers("""{"x":1}"""))
    }
}
