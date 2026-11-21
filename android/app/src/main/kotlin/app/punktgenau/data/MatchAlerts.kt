package app.punktgenau.data

import java.time.Duration
import java.time.LocalDateTime

/**
 * Welche Spielbenachrichtigungen fällig sind. Reine Logik ohne Android, damit sie testbar bleibt.
 *
 * - Ein Spiel, das in den nächsten [SOON_WINDOW] beginnt, wird einmal angekündigt.
 * - Ein Ergebnis wird einmal gemeldet, sobald es im Spielplan steht.
 * Was schon gemeldet wurde, steht in [sent] und wird nicht noch einmal gemeldet.
 */
object MatchAlerts {
    /** Vorlauf der Ankündigung. Der Abruf läuft alle drei Stunden; so kommt jedes Spiel rechtzeitig vorab. */
    val SOON_WINDOW: Duration = Duration.ofHours(3)

    data class Alert(val key: String, val title: String, val text: String)

    fun plan(matches: List<SpieltagMatch>, now: LocalDateTime, sent: Set<String>): List<Alert> {
        val out = ArrayList<Alert>()
        for (m in matches) {
            val a = m.appointment
            val start = MatchTime.local(a.date) ?: continue
            val base = key(m)
            val result = a.result?.takeIf { it.isNotBlank() }
            if (result != null) {
                val k = "result|$base|$result"
                if (k !in sent) out += Alert(k, "Ergebnis: ${m.team.name}", "${m.team.name} – ${a.opponentName}: $result")
            } else if (start.isAfter(now) && Duration.between(now, start) <= SOON_WINDOW) {
                val k = "soon|$base"
                if (k !in sent) {
                    out += Alert(
                        k,
                        "Spiel heute um %02d:%02d".format(start.hour, start.minute),
                        "${m.team.name} – ${a.opponentName}",
                    )
                }
            }
        }
        return out
    }

    /** Stabiler Schlüssel eines Spiels: Mannschaft, Anstoß und Gegner. */
    fun key(m: SpieltagMatch): String = "${m.team.id}|${m.appointment.date}|${m.appointment.opponentTeamId}"
}

/** Gemerkte Benachrichtigungen, damit kein Spiel zweimal gemeldet wird. Begrenzt auf die jüngsten Einträge. */
class AlertStore(private val storage: StringSetStorage) {
    fun sent(): Set<String> = storage.get()

    fun add(keys: Collection<String>) {
        if (keys.isEmpty()) return
        // Die Reihenfolge ist nicht garantiert; begrenzt wird willkürlich, aber nur bei sehr vielen Einträgen.
        val all = storage.get() + keys
        storage.put(if (all.size > MAX) all.toList().takeLast(MAX).toSet() else all)
    }

    companion object {
        private const val MAX = 300
    }
}
