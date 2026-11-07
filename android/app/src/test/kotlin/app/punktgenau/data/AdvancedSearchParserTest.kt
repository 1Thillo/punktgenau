package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvancedSearchParserTest {

    @Test fun ranglisteMitSeitenzahl() {
        val page = AdvancedSearchParser.page(
            """{"pageContent":{"id":"rankings/andro-rangliste"},"blockLoaderData":{"abc":{"area":"","entries":[
              {"personId":"NU2356279","firstname":"Max","lastname":"Muster","clubName":"TuS Beispiel","position":"1","rank":"1234","gender":"male"},
              {"personId":"NU1","firstname":"","lastname":"","clubName":"X","position":"2"},
              {"personId":"NU9","firstname":"Erika","lastname":"Probe","clubName":null,"position":"3","gender":"female"}],
              "resultLength":3,"numberOfPages":15,"currentPage":0}}}""",
        )!!
        assertEquals(2, page.players.size)
        assertEquals("NU2356279", page.players[0].nuId)
        assertEquals("Max Muster", "${page.players[0].firstName} ${page.players[0].lastName}")
        assertEquals("TuS Beispiel", page.players[0].club)
        assertEquals(1, page.players[0].rank)
        assertNull(page.players[1].club)
        assertEquals(15, page.pages)
    }

    @Test fun ohneBlockKeinErgebnis() {
        assertNull(AdvancedSearchParser.page("""{"pageContent":{}}"""))
        assertNull(AdvancedSearchParser.page("<html>"))
    }
}
