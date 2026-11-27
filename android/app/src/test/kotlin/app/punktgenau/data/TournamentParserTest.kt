package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TournamentParserTest {
    private val html = """
        <script>window.x={"calendar":{"error":null,"data":{"count":2,"tournaments":[
          {"id":"822301","host":"Deutscher Tischtennis-Bund e.V.","name":"Top 48 [Jugend 15] \"Cup\"","type":"qualification","state":"publication",
           "location":"Essen","location_zip":"45149","start_date_time":"2026-11-27T13:00:00+00:00","organization_short":"DTTB",
           "registration_deadline":"2026-11-12T22:59:00.000+00:00","formattedStartDate":"27.11.2026","formattedStartTime":"14:00","formattedEndDate":"29.11.2026"},
          {"id":"862360","host":"SuS BW Lipperbruch","name":"BWL Advents-Speedcups","type":"open","state":"publication",
           "location":"Lippstadt","start_date_time":"2026-11-25T18:15:00+00:00","organization_short":"WTTV",
           "registration_deadline":null,"formattedStartDate":"25.11.2026","formattedEndDate":"09.12.2026"}
        ]}}}</script>
    """.trimIndent()

    @Test
    fun readsAllTournamentsFromCalendarHtml() {
        val list = TournamentParser.parse(html)!!
        assertEquals(2, list.size)
        val first = list[0]
        assertEquals("822301", first.id)
        assertEquals("Top 48 [Jugend 15] \"Cup\"", first.name)
        assertEquals("qualification", first.type)
        assertEquals("2026-11-27", first.startDay)
        assertEquals("27.11.2026", first.startText)
        assertEquals("14:00", first.startTime)
        assertEquals("29.11.2026", first.endText)
        assertEquals("WTTV", list[1].organization)
    }

    @Test
    fun missingDeadlineIsNull() {
        val list = TournamentParser.parse(html)!!
        assertNull(list[1].registrationDeadline)
    }

    @Test
    fun pageWithoutListGivesNull() {
        assertNull(TournamentParser.parse("<html>kein Kalender</html>"))
    }
}
