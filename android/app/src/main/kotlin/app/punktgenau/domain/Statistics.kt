package app.punktgenau.domain

import app.punktgenau.data.HistoryEvent

/** Auswertung eines Zeitraums aus dem TTR-Verlauf. */
data class StatsSummary(
    /** Anzahl Mannschaftsspiele (Ereignisse vom Typ "meeting"). */
    val meetings: Int,
    /** Mannschaftsspiele, die mit mehr gewonnenen als verlorenen Einzeln endeten. */
    val meetingWins: Int,
    /** Mannschaftsspiele mit mehr verlorenen Einzeln. */
    val meetingLosses: Int,
    /** Mannschaftsspiele mit gleich vielen gewonnenen und verlorenen Einzeln. */
    val meetingDraws: Int,
    /** Gewonnene und verlorene Einzelspiele über alle Mannschaftsspiele. */
    val matchesWon: Int,
    val matchesLost: Int,
    /** Größter TTR-Zuwachs (Ereignis mit dem höchsten positiven Delta); null, wenn es keinen gibt. */
    val biggestGain: HistoryEvent?,
    /** Größter TTR-Verlust (Ereignis mit dem kleinsten negativen Delta); null, wenn es keinen gibt. */
    val biggestLoss: HistoryEvent?,
) {
    /** Siegquote der Einzelspiele in Prozent (0–100), null ohne Einzelspiele. */
    val matchWinRate: Int?
        get() = (matchesWon + matchesLost).takeIf { it > 0 }?.let { Math.round(matchesWon * 100.0 / it).toInt() }
}

object Statistics {
    fun summarize(events: List<HistoryEvent>): StatsSummary {
        val meetings = events.filter { it.type == "meeting" }
        return StatsSummary(
            meetings = meetings.size,
            meetingWins = meetings.count { it.won > it.lost },
            meetingLosses = meetings.count { it.lost > it.won },
            meetingDraws = meetings.count { it.won == it.lost },
            matchesWon = meetings.sumOf { it.won },
            matchesLost = meetings.sumOf { it.lost },
            biggestGain = events.filter { it.delta > 0 }.maxByOrNull { it.delta },
            biggestLoss = events.filter { it.delta < 0 }.minByOrNull { it.delta },
        )
    }
}
