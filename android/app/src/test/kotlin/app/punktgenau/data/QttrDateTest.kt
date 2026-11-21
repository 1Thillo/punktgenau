package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QttrDateTest {
    @Test fun datumWirdAlsTagMonatJahrAngezeigt() {
        val body = """{"ttr":null,"vq_ttr":null,"ttr_last_fixed":"1267","ttr_last_fixed_date":"2026-08-11"}"""
        assertEquals("11.08.2026", ProfileParser.qttrDate(body))
    }

    @Test fun ohneDatum_istNull() {
        assertNull(ProfileParser.qttrDate("""{"ttr":1293,"ttr_last_fixed":"1267"}"""))
    }

    @Test fun ungueltigesDatum_istNull() {
        assertNull(ProfileParser.qttrDate("""{"ttr_last_fixed_date":"11.08.2026"}"""))
    }

    @Test fun keinJson_istNull() {
        assertNull(ProfileParser.qttrDate("<html>Anmelden</html>"))
    }
}
