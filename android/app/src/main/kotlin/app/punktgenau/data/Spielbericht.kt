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

/** Spielbericht der öffentlichen click-tt-Seite (HTML); die Website kann eine Bestätigung verlangen. */
class SpielberichtRepository(private val session: SessionManager) {

    suspend fun load(url: String): Result<List<GameLine>> = try {
        val r = session.fetch(Request.Builder().url(url).build())
        if (r.body.contains("/verify") || r.body.contains("Verifizierung")) {
            Result.Failed("Die Website verlangt eine Bestätigung. Den Bericht kannst du im Browser öffnen.")
        } else {
            val games = SpielberichtParser.games(r.body)
            if (games.isEmpty()) Result.Failed("Im Spielbericht stehen keine Einzel.") else Result.Ok(games)
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

    private fun names(s: String): List<String> = s.split("/").map { it.trim() }.filter { it.isNotEmpty() }
}
