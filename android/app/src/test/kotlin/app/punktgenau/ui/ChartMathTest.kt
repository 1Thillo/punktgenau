package app.punktgenau.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartMathTest {
    @Test
    fun nearestPointByX() {
        val xs = listOf(100f, 200f, 400f)
        assertEquals(0, ChartMath.nearestIndex(xs, 0f))
        assertEquals(1, ChartMath.nearestIndex(xs, 240f))
        assertEquals(2, ChartMath.nearestIndex(xs, 310f))
        assertEquals(2, ChartMath.nearestIndex(xs, 9999f))
        assertNull(ChartMath.nearestIndex(emptyList(), 5f))
    }

    /** Zwei Ereignisse am selben Tag liegen übereinander; gezeigt wird das spätere, also der Stand am Tagesende. */
    @Test
    fun sameDayPicksLaterEvent() {
        assertEquals(1, ChartMath.nearestIndex(listOf(100f, 100f, 300f), 90f))
    }

    @Test
    fun ticksAlwaysContainLowestAndHighest() {
        assertEquals(listOf(1400, 1433, 1466, 1500), ChartMath.ticks(1400, 1500))
        assertEquals(listOf(1400, 1401), ChartMath.ticks(1400, 1401))
        assertEquals(listOf(1400), ChartMath.ticks(1400, 1400))
    }

    @Test
    fun xPositionsSpreadByDate() {
        assertEquals(listOf(50f, 100f, 250f), ChartMath.xPositions(listOf(10L, 15L, 30L), 50f, 250f))
        assertEquals(listOf(50f), ChartMath.xPositions(listOf(10L), 50f, 250f))
    }
}
