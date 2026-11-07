package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileParserTest {
    @Test fun startseite_liestIdUndName() {
        val html = """<script>{"userProfile":{"firstname":"Erika","lastname":"Muster","clicktt_id":12345}}</script>"""
        val p = ProfileParser.parseStartPage(html)
        assertEquals("NU12345", p.nuId)
        assertEquals("Erika Muster", p.name)
    }

    @Test fun startseite_ohneProfil() {
        val p = ProfileParser.parseStartPage("<html></html>")
        assertNull(p.nuId)
        assertNull(p.name)
    }

    @Test fun verlauf_liestTtrUndQttr() {
        assertEquals(1520 to 1498, ProfileParser.parseHistory("""{"ttr":1520,"vq_ttr":1498,"events":[]}"""))
    }

    @Test fun verlauf_ohneTtr_nurQttr() {
        assertEquals(null to 1498, ProfileParser.parseHistory("""{"vq_ttr":1498}"""))
    }

    @Test fun verlauf_kaputtesJson_keinAbsturz() {
        assertEquals(null to null, ProfileParser.parseHistory("<html>"))
    }
}

class RatingOnTest {
    private val body = """{"ttr":1300,"vq_ttr":1290,"event":[
        {"event_date_time":"2026-10-29 19:00:00","ttr_before":1280,"ttr_after":1288},
        {"event_date_time":"2026-11-06 19:00:00","ttr_before":1288,"ttr_after":1299}]}"""
    private val events = ProfileParser.parseEvents(body)

    @org.junit.Test fun stand_vorDemTag() {
        org.junit.Assert.assertEquals(1288, ProfileParser.ratingOn(events, java.time.LocalDate.of(2026, 11, 6)))
        org.junit.Assert.assertEquals(1299, ProfileParser.ratingOn(events, java.time.LocalDate.of(2026, 11, 20)))
    }

    @org.junit.Test fun vorDemErstenEreignis_nimmtBefore() =
        org.junit.Assert.assertEquals(1280, ProfileParser.ratingOn(events, java.time.LocalDate.of(2026, 10, 1)))

    @org.junit.Test fun ohneEreignisse_null() =
        org.junit.Assert.assertNull(ProfileParser.ratingOn(ProfileParser.parseEvents("""{"ttr":1}"""), java.time.LocalDate.of(2026, 1, 1)))
}
