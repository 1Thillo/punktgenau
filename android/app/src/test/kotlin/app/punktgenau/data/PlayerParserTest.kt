package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerParserTest {
    @Test fun liestTreffer() {
        val body = """{"results":[{"lastname":"Muster","firstname":"Erika","person_id":1,"internal_id":"NU99","club_name":"TTC Beispiel"}]}"""
        val hits = PlayerParser.parseSearch(body)!!
        assertEquals(1, hits.size)
        assertEquals("Erika Muster", hits[0].displayName)
        assertEquals("NU99", hits[0].nuId)
        assertEquals("TTC Beispiel", hits[0].club)
    }

    @Test fun leereListe() = assertEquals(0, PlayerParser.parseSearch("""{"results":[]}""")!!.size)

    @Test fun unlesbar_gibtNull() {
        assertNull(PlayerParser.parseSearch("<html>"))
        assertNull(PlayerParser.parseSearch("""{"foo":1}"""))
    }
}
