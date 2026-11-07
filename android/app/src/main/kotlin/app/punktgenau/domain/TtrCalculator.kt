package app.punktgenau.domain

import kotlin.math.floor
import kotlin.math.pow

/** Ein Einzel gegen einen Gegner mit bekanntem Wert vor dem Termin. */
data class Game(val opponentRating: Int, val won: Boolean)

enum class AgeGroup(val surcharge: Int) {
    ADULT(0),
    UNDER_21(4),

    /** Unter 16 zählt zusätzlich zu "unter 21": insgesamt +8. */
    UNDER_16(8),
}

/** Die vier Angaben, aus denen sich die Änderungskonstante ergibt. */
data class AlterationInput(
    val ageGroup: AgeGroup = AgeGroup.ADULT,
    val fewerThan30Singles: Boolean = false,
    val breakOfAYearThenFewerThan15: Boolean = false,
)

data class TtrResult(
    val ownRating: Int,
    val alterationConstant: Int,
    val expectedSum: Double,
    val actualSum: Int,
    val change: Int,
) {
    val newRating: Int get() = ownRating + change
}

object TtrCalculator {
    const val BASE_CONSTANT = 16
    private const val SCALE = 150.0

    fun winExpectation(own: Int, opponent: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponent - own) / SCALE))

    fun alterationConstant(input: AlterationInput): Int =
        BASE_CONSTANT +
            input.ageGroup.surcharge +
            (if (input.fewerThan30Singles) 4 else 0) +
            (if (input.breakOfAYearThenFewerThan15) 4 else 0)

    fun calculate(ownRating: Int, games: List<Game>, alterationConstant: Int): TtrResult {
        val expected = games.sumOf { winExpectation(ownRating, it.opponentRating) }
        val actual = games.count { it.won }
        val change = roundHalfUp((actual - expected) * alterationConstant)
        return TtrResult(ownRating, alterationConstant, expected, actual, change)
    }

    fun calculate(ownRating: Int, games: List<Game>, input: AlterationInput): TtrResult =
        calculate(ownRating, games, alterationConstant(input))

    /** Wie `Math.round`: 0,5 wird aufgerundet, bei negativen Werten Richtung 0. */
    internal fun roundHalfUp(value: Double): Int = floor(value + 0.5).toInt()
}
