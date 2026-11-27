package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferRegionsTest {
    @Test
    fun readsRegionsFromWechsellisteData() {
        val body = """{"data":{"playerTransfers":[],"licensePeriods":[],"nuRegions":[
            {"name":"Rheinland-Rheinhessen","pattern":"DE.SW.R7.12"},
            {"name":"Koblenz/Neuwied","pattern":"DE.SW.R7.12.01"}]}}"""
        val page = TransferParser.page(body)!!
        assertEquals(
            listOf(TransferRegion("Rheinland-Rheinhessen", "DE.SW.R7.12"), TransferRegion("Koblenz/Neuwied", "DE.SW.R7.12.01")),
            page.regions,
        )
    }

    @Test
    fun missingRegionsGiveEmptyList() {
        val page = TransferParser.page("""{"data":{"playerTransfers":[],"licensePeriods":[]}}""")!!
        assertEquals(emptyList<TransferRegion>(), page.regions)
    }
}
