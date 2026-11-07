package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickTtParserTest {
    private fun root(s: String) = ClickTtParser.root(s)!!

    @Test fun bezirke() {
        val r = root("""{"data":{"districts":[{"name":"Koblenz/Neuwied","championship":"Ko/Nwd 26/27"},{"name":"Ahrweiler","championship":"AW/MY/COC 26/27"}]}}""")
        assertEquals(listOf("Koblenz/Neuwied", "Ahrweiler"), ClickTtParser.districts(r)!!.map { it.name })
        assertEquals("AW/MY/COC 26/27", ClickTtParser.districts(r)!![1].championship)
    }

    @Test fun ligenNachKategorie() {
        val r = root("""{"data":[{"contest":"Erwachsene","groups":[{"name":"Kreisliga Gr. B","group_urlid":"518332"}]},{"contest":"Ju 19","groups":[{"name":"Kreisliga (HR)","group_urlid":"518335"}]}]}""")
        val g = ClickTtParser.groups(r)!!
        assertEquals(listOf("Erwachsene", "Ju 19"), g.map { it.category })
        assertEquals("518335", g[1].groupId)
    }

    @Test fun tabelle() {
        val r = root("""{"data":{"league_ttr_avg":1254,"league_table":[{"table_rank":1,"team_id":"7","team_name":"TTG Pellenz IV","meetings_count":6,"meetings_won":4,"meetings_tie":1,"meetings_lost":1,"points_won":9,"points_lost":3,"matches_won":40,"matches_lost":20,"rise_fall_state":"rise"}]}}""")
        val t = ClickTtParser.table(r)!!
        assertEquals(1254, t.ttrAverage)
        assertEquals("TTG Pellenz IV", t.rows[0].teamName)
        assertEquals(9, t.rows[0].pointsWon)
        assertEquals(40, t.rows[0].matchesWon)
    }

    @Test fun gruppenSpielplan() {
        val r = root("""{"data":{"meetings":[{"2027-01-22":[{"date":"2027-01-22T19:00:00.000+00:00","team_home":"A","team_away":"B","team_home_id":"1","team_away_id":"2","matches_won":"9","matches_lost":"3"}]},{"2026-10-01":[{"date":"2026-10-01T19:00:00.000+00:00","team_home":"C","team_away":"D"}]}]}}""")
        val m = ClickTtParser.meetings(r)!!
        assertEquals(listOf("C", "A"), m.map { it.home })
        assertEquals("9:3", m[1].result)
        assertNull(m[0].result)
    }

    @Test fun bilanzRangliste() {
        val r = root("""{"data":{"ranking":[{"team_id":"3","team_name":"FSV","player_id":"NU1","player_rank":1,"player_rating":"8.825","player_firstname":"Stefan","player_lastname":"Hartung","player_team_rank":1,"player_matches_won":9,"player_matches_lost":1,"player_position_statistics":[{"position":1,"position_matches_won":4,"position_matches_lost":1},{"position":2,"position_matches_won":5,"position_matches_lost":0}]}]}}""")
        val b = ClickTtParser.ranking(r)!![0]
        assertEquals("Stefan Hartung", b.name)
        assertEquals("8.825", b.rating)
        assertEquals(listOf(1 to "4:1", 2 to "5:0"), b.positions)
    }

    @Test fun mannschaftsSpieler() {
        val r = root("""{"data":{"balancesheet":[{"single_player_statistics":[{"player_id":"NU9","points_won":"8","points_lost":"4","player_rank":"4","team_number":"2","meeting_count":"6","player_firstname":"Thilo","player_lastname":"Gilles","single_statistics":[{"points_won":"1","points_lost":"2","opponent_rank":"1"}]}]}]}}""")
        val p = ClickTtParser.teamPlayers(r)!![0]
        assertEquals("2.4", p.position)
        assertEquals(6, p.appearances)
        assertEquals(OpponentLine(1, 1, 2), p.perOpponent[0])
    }

    @Test fun unlesbar() {
        assertNull(ClickTtParser.root("<html>"))
        assertNull(ClickTtParser.table(root("""{"data":{}}""")))
        assertNull(ClickTtParser.meetings(root("""{"x":1}""")))
    }
}
