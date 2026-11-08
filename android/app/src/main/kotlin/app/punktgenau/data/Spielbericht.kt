package app.punktgenau.data

import app.punktgenau.data.session.SessionManager
import okhttp3.Request

/**
 * Eine Zeile des Spielberichts: Einzel oder Doppel mit Namen je Seite, Sätze und Gesamtstand.
 * [setScores] sind die einzelnen Satzergebnisse (S1 bis S5), soweit gespielt.
 */
data class GameLine(
    val rank: String,
    val home: List<String>,
    val away: List<String>,
    val sets: String,
    val total: String,
    val setScores: List<String>,
)

/**
 * Summen am Ende des Spielberichts: Bälle (z. B. "358:366"), gewonnene Sätze und gewonnene Spiele des
 * Mannschaftskampfs (z. B. "18:21" und "4:6"). [balls] fehlt, wenn die Website keine Bälle nennt.
 */
data class Totals(val balls: String?, val sets: String, val total: String)

/** Spielbericht mit Einzeln und Summenzeile. */
data class SpielberichtReport(val games: List<GameLine>, val totals: Totals?)

/** Spielbericht der öffentlichen click-tt-Seite (HTML); die Website kann eine Bestätigung verlangen. */
class SpielberichtRepository(private val session: SessionManager) {

    /** Ein Bericht ändert sich nach dem Spiel kaum; so wird er nicht bei jedem Öffnen neu geholt. */
    private val cache = TtlCache<String, SpielberichtReport>(30 * 60 * 1000L)

    /** [fresh] umgeht den Zwischenspeicher (für laufende Spiele). */
    suspend fun load(url: String, fresh: Boolean = false): Result<SpielberichtReport> = try {
        if (!fresh) cache.get(url)?.let { return Result.Ok(it) }
        val r = session.fetch(Request.Builder().url(url).build())
        if (r.body.contains("/verify") || r.body.contains("Verifizierung")) {
            Result.Failed("Die Website verlangt eine Bestätigung. Den Bericht kannst du im Browser öffnen.")
        } else {
            val games = SpielberichtParser.games(r.body)
            if (games.isEmpty()) {
                Result.Failed("Im Spielbericht stehen keine Einzel.")
            } else {
                val report = SpielberichtReport(games, SpielberichtParser.totals(r.body))
                cache.put(url, report)
                Result.Ok(report)
            }
        }
    } catch (e: java.io.IOException) {
        Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
    }
}

object SpielberichtParser {
    private val row = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val cell = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL)
    private val tags = Regex("<[^>]+>")
    private val spaces = Regex("\\s+")
    private val score = Regex("^\\d+:\\d+$")

    private fun clean(s: String): String =
        spaces.replace(tags.replace(s, " ").replace("&nbsp;", " ").replace("&amp;", "&"), " ").trim()

    /**
     * Spalten: Rang, Heim, Gast, S1 bis S5, Sätze, Gesamt. Zeilen ohne Satzergebnis in der Spalte "Sätze"
     * fallen weg (Kopfzeilen, Leerzeilen).
     */
    fun games(html: String): List<GameLine> = row.findAll(html).mapNotNull { m ->
        val cells = cell.findAll(m.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
        if (cells.size < 10 || !score.matches(cells[8])) return@mapNotNull null
        GameLine(
            rank = cells[0],
            home = names(cells[1]),
            away = names(cells[2]),
            sets = cells[8],
            total = cells[9],
            setScores = cells.subList(3, 8).filter { score.matches(it) },
        )
    }.toList()

    /**
     * Summenzeile "Bälle x:y | Sätze | Gesamt". Die Zelle mit den Bällen ist kein Satzergebnis; Sätze und
     * Gesamt sind die letzten beiden Ergebnisse der Zeile.
     */
    fun totals(html: String): Totals? {
        val ballsRx = Regex("^Bälle\\s*(\\d+:\\d+)$")
        for (m in row.findAll(html)) {
            val cells = cell.findAll(m.groupValues[1]).map { clean(it.groupValues[1]) }.toList()
            val balls = cells.firstNotNullOfOrNull { ballsRx.find(it)?.groupValues?.get(1) } ?: continue
            val scores = cells.filter { score.matches(it) }
            if (scores.size < 2) continue
            return Totals(balls, scores[scores.size - 2], scores.last())
        }
        return null
    }

    private fun names(s: String): List<String> = s.split("/").map { it.trim() }.filter { it.isNotEmpty() }
}
