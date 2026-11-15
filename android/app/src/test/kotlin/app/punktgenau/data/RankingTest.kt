package app.punktgenau.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingTest {
    private val a = RankEntry("NU1", "A", "M1", ttr = 1500, qttr = 1400)
    private val b = RankEntry("NU2", "B", "M1", ttr = 1450, qttr = 1480)
    private val onlyQ = RankEntry("NU3", "C", "M2", ttr = null, qttr = 1600)
    private val onlyT = RankEntry("NU4", "D", "M2", ttr = 1300, qttr = null)

    @Test
    fun sortedByTtr_missingValuesLast() {
        assertEquals(listOf("A", "B", "D", "C"), Ranking.sorted(listOf(onlyQ, b, onlyT, a), RatingMode.TTR).map { it.name })
    }

    @Test
    fun sortedByQttr_missingValuesLast() {
        assertEquals(listOf("C", "B", "A", "D"), Ranking.sorted(listOf(onlyT, a, b, onlyQ), RatingMode.QTTR).map { it.name })
    }

    @Test
    fun valueFollowsMode() {
        assertEquals(1500, Ranking.value(a, RatingMode.TTR))
        assertEquals(1400, Ranking.value(a, RatingMode.QTTR))
        assertEquals(null, Ranking.value(onlyQ, RatingMode.TTR))
    }
}
