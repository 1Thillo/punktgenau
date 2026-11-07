package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClubParserTest {

    @Test fun spiellokaleAusKontakten() {
        val info = ClubParser.info(
            """{"data":{"club_name":"TuS 1926 Kehrig e. V.","region_name":"Ahrweiler","club_infos":{
              "club_contacts":[
                {"label":"Kontaktadresse","street":"In der Mohl 6","zipcode":"56729","city":"Kehrig","contact_name":"Karl Heinz Martini","phone_home":"02651/73350"},
                {"label":"Spiellokal 1","street":"Am St. Wolfgang","zipcode":"56729","city":"Kehrig","contact_name":"Elztalhalle Kehrig"}],
              "club_officials":[]}}}""",
        )!!
        assertEquals("Karl Heinz Martini", info.contactName)
        assertEquals(1, info.venues.size)
        assertEquals("Elztalhalle Kehrig", info.venues[0].name)
        assertEquals("Am St. Wolfgang, 56729 Kehrig", info.venues[0].query)
    }

    @Test fun adresseOhneOrtBleibtLesbar() {
        assertEquals("Hauptstr. 1", Venue("", "Hauptstr. 1", "", "").query)
        assertEquals("56729 Kehrig", Venue("", "", "56729", "Kehrig").query)
    }

    @Test fun teamKontaktUndHeimspiellokal() {
        val root = ClickTtParser.root(
            """{"clubData":{"team_name":"TuS Kehrig IV","team_contact":{"contact_name":"Markus Hoffmann","phone_mobile":"0172/6902880","phone_home":"026769526773","email_home":"m@example.de"}},
              "data":{"meetings_excerpt":{"meetings":[
                {"2026-09-15":[
                  {"team_home":"TuS Kehrig IV","team_away":"SV Mendig VII","location":{"zip":"56729","city":"Kehrig","label":"Elztalhalle Kehrig","street":"Am St. Wolfgang"}},
                  {"team_home":"SV Mendig VII","team_away":"TuS Kehrig IV","location":{"zip":"56727","city":"Mendig","label":"Halle Mendig","street":"Sportplatz 2"}}]},
                {"2026-10-01":[
                  {"team_home":"TuS Kehrig IV","team_away":"TTC X","location":{"zip":"56729","city":"Kehrig","label":"Elztalhalle Kehrig","street":"Am St. Wolfgang"}}]}]}}}""",
        )!!
        val d = ClickTtParser.teamDetails(root)!!
        assertEquals("Markus Hoffmann", d.contact!!.name)
        assertEquals("0172/6902880", d.contact?.phone)
        assertEquals("m@example.de", d.contact?.email)
        assertEquals(listOf("Am St. Wolfgang, 56729 Kehrig"), d.homeVenues.map { it.query })
    }

    @Test fun ohneClubDataKeinErgebnis() {
        assertNull(ClickTtParser.teamDetails(ClickTtParser.root("""{"data":{}}""")!!))
    }
}
