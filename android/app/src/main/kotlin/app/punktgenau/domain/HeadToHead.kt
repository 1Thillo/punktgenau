package app.punktgenau.domain

import app.punktgenau.data.GameLine

/** Bilanz zweier Spieler: [lines] Begegnungen, in denen beide auf gegnerischen Seiten standen; [aWins]/[bWins] davon gewonnen. */
data class HeadToHead(val lines: Int, val aWins: Int, val bWins: Int) {
    operator fun plus(o: HeadToHead) = HeadToHead(lines + o.lines, aWins + o.aWins, bWins + o.bWins)

    companion object {
        val NONE = HeadToHead(0, 0, 0)
    }
}

object HeadToHeadMath {
    private enum class Side { HOME, AWAY }

    /** Zählt die Begegnungen zwischen den Spielern [a] und [b] (nuId). Zeilen ohne Kennung oder ohne lesbaren Satzstand fallen weg. */
    fun count(games: List<GameLine>, a: String, b: String): HeadToHead {
        if (a == b) return HeadToHead.NONE
        var lines = 0
        var aWins = 0
        var bWins = 0
        for (g in games) {
            val sa = sideOf(a, g) ?: continue
            val sb = sideOf(b, g) ?: continue
            if (sa == sb) continue
            val homeWon = homeWon(g.sets) ?: continue
            lines++
            // Die Heimseite hat gewonnen: Wer auf Heim steht, hat gewonnen, und umgekehrt.
            val aWon = (sa == Side.HOME) == homeWon
            if (aWon) aWins++ else bWins++
        }
        return HeadToHead(lines, aWins, bWins)
    }

    private fun sideOf(id: String, g: GameLine): Side? = when {
        id in g.homeIds -> Side.HOME
        id in g.awayIds -> Side.AWAY
        else -> null
    }

    /** true = Heim gewinnt, false = Gast gewinnt, null = offen oder unlesbar (Satzstand "3:1"). */
    private fun homeWon(sets: String): Boolean? {
        val p = sets.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (p.size != 2 || p[0] == p[1]) return null
        return p[0] > p[1]
    }
}
