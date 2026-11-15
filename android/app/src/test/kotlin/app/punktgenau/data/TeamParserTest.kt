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

    @Test fun spielplan_ausClickTtSeite() {
        val body = """{"data":{"meetings_excerpt":{"meetings":[
            {"2027-02-20":[{"date":"2027-02-20T19:00:00.000+00:00","team_home":"DJK Mayen III","team_away":"TTG Nettetal II","team_home_id":"10","team_away_id":"1","matches_won":"0","matches_lost":"0"}]},
            {"2026-10-29":[{"date":"2026-10-29T19:00:00.000+00:00","team_home":"TTG Nettetal II","team_away":"TuS Kehrig IV","team_home_id":"1","team_away_id":"11","matches_won":"9","matches_lost":"3"}]}]}}}"""
        val list = TeamParser.parseSchedule(body, "1")!!
        assertEquals(listOf("TuS Kehrig IV", "DJK Mayen III"), list.map { it.opponentName })
        assertEquals(listOf(true, false), list.map { it.home })
        assertEquals("9:3", list[0].result)
        assertNull(list[1].result)
        assertEquals("10", list[1].opponentTeamId)
    }

    @Test fun spielplan_eigenerName_auswaertsUndHeim() {
        val body = """{"data":{"meetings_excerpt":{"meetings":[
            {"2026-10-29":[{"date":"2026-10-29T19:00:00.000+00:00","team_home":"TuS Kaisersesch Erwachsene II","team_away":"DJK Mayen III","team_home_id":"5","team_away_id":"6"}]},
            {"2026-11-05":[{"date":"2026-11-05T19:00:00.000+00:00","team_home":"DJK Mayen III","team_away":"TuS Kaisersesch Erwachsene II","team_home_id":"6","team_away_id":"5"}]}]}}}"""
        val list = TeamParser.parseSchedule(body, "5")!!
        assertEquals(listOf("TuS Kaisersesch Erwachsene II", "TuS Kaisersesch Erwachsene II"), list.map { it.ownName })
        assertEquals(listOf("DJK Mayen III", "DJK Mayen III"), list.map { it.opponentName })
    }

    @Test fun spielplan_laufendesSpielAusLiveFeld() {
        val body = """{"data":{"meetings_excerpt":{"meetings":[
            {"2026-11-12":[{"date":"2026-11-12T19:00:00.000+00:00","team_home":"A","team_away":"B","team_home_id":"1","team_away_id":"2","live":"1"}]},
            {"2026-11-12":[{"date":"2026-11-12T19:00:00.000+00:00","team_home":"A","team_away":"C","team_home_id":"1","team_away_id":"3","live":"0"}]},
            {"2026-11-19":[{"date":"2026-11-19T19:00:00.000+00:00","team_home":"A","team_away":"D","team_home_id":"1","team_away_id":"4","state":"live"}]},
            {"2026-11-26":[{"date":"2026-11-26T19:00:00.000+00:00","team_home":"A","team_away":"E","team_home_id":"1","team_away_id":"5"}]}]}}}"""
        val list = TeamParser.parseSchedule(body, "1")!!
        assertEquals(listOf(true, false, true, false), list.map { it.live })
    }

    /** Felder wie auf der Website am 15.11.2026: `live` als Wahrheitswert, `state`, Vereinsnummern, Spielkennung. */
    @Test fun spielplan_echteFelder() {
        val body = """{"data":{"meetings_excerpt":{"meetings":[
            {"2026-11-13":[{"date":"2026-11-13T19:00:00.000+00:00","live":false,"state":"done","league_id":"518414","team_away":"TTG Nettetal II","team_home":"TTV Andernach III","meeting_id":"15898557","matches_won":"8","matches_lost":"2","team_away_id":"3081442","team_home_id":"3142298","team_away_club_id":"002014","team_home_club_id":"004314","is_meeting_complete":true,"has_nu_live_push_data":false,"nu_score_live_enabled":false}]},
            {"2026-11-17":[{"date":"2026-11-17T18:15:00.000+00:00","live":false,"state":"scheduled","league_id":"518414","team_away":"TTG Nettetal II","team_home":"SG Nachtsheim e. V.","meeting_id":"15898570","matches_won":"0","matches_lost":"0","team_away_id":"3081442","team_home_id":"3082372","team_away_club_id":"002014","team_home_club_id":"000516","is_meeting_complete":false}]},
            {"2026-11-20":[{"date":"2026-11-20T19:00:00.000+00:00","live":true,"state":"scheduled","league_id":"518414","team_away":"X","team_home":"TTG Nettetal II","meeting_id":"1","matches_won":"0","matches_lost":"0","team_away_id":"9","team_home_id":"3081442"}]}]}}}"""
        val list = TeamParser.parseSchedule(body, "3081442")!!
        assertEquals(listOf(false, false, true), list.map { it.live })
        assertEquals(listOf("done", "scheduled", "scheduled"), list.map { it.state })
        assertEquals(listOf(true, false, false), list.map { it.complete })
        assertEquals("8:2", list[0].result)
        assertNull(list[1].result)
        // Die Spielkennung steht schon vor dem Spiel fest; daran hängt der Zugang zum Spielbericht.
        assertEquals("15898570", list[1].meetingId)
        assertEquals("002014", list[0].ownClubNr)
        assertEquals("004314", list[0].opponentClubNr)
    }

    @Test fun spielplan_fremdesSpielWirdIgnoriert() {
        val body = """{"data":{"meetings_excerpt":{"meetings":[{"2026-10-29":[{"date":"x","team_home":"A","team_away":"B","team_home_id":"5","team_away_id":"6"}]}]}}}"""
        assertEquals(0, TeamParser.parseSchedule(body, "1")!!.size)
    }

    @Test fun spieler_nachRang() {
        val body = """{"data":[{"firstname":"B","lastname":"Zwei","internal_id":"NU2","rank":2},{"firstname":"A","lastname":"Eins","internal_id":"NU1","rank":1}]}"""
        assertEquals(listOf("A Eins", "B Zwei"), TeamParser.parsePlayers(body)!!.map { it.displayName })
    }

    @Test fun unlesbar() {
        assertNull(TeamParser.parseSchedule("<html>", "1"))
        assertNull(TeamParser.parsePlayers("""{"x":1}"""))
    }
}
