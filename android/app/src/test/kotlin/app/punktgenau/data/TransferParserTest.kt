package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferParserTest {

    @Test fun wechselUndZeitraeume() {
        val page = TransferParser.page(
            """{"association":"RTTVR","playerTransfers":[
              {"firstname":"Samer","lastname":"Alyousef","gender":"M","old_club_name":"SPONETA Erfurt","new_club_name":"TTC Gelb-Rot Trier","licence_transfer_part":["adultsTeamMatches"]},
              {"firstname":"Volker","lastname":"Gauer","old_club_name":"TTC DJK Hennef","new_club_name":"SV Rheinbreitbach","licence_transfer_part":["TLEI","seniorsTeamMatches"]}],
              "licensePeriods":[{"from":"2027-01-01","to":"2027-06-30","label":"Rückrunde 26/27"},{"from":"2026-07-01","to":"2026-12-31","label":"Vorrunde 26/27"}]}""",
        )!!
        assertEquals(2, page.transfers.size)
        assertEquals("Samer Alyousef", page.transfers[0].name)
        assertEquals("TTC Gelb-Rot Trier", page.transfers[0].newClub)
        assertEquals(listOf("TLEI", "seniorsTeamMatches"), page.transfers[1].parts)
        assertEquals(listOf("2027-01-01", "2026-07-01"), page.periods.map { it.from })
        assertEquals("Vorrunde 26/27", page.periods[1].label)
    }

    @Test fun ohneListeKeinErgebnis() {
        assertNull(TransferParser.page("""{"association":"RTTVR"}"""))
        assertNull(TransferParser.page("<html>"))
    }
}
