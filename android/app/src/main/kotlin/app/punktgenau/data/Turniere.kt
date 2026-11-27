package app.punktgenau.data

import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Request

/** Ein Turnier aus dem öffentlichen Turnierkalender. [type]: open (offen), qualification (Qualifikation), circuit (Turnierserie). */
data class Tournament(
    val id: String,
    val name: String,
    val host: String,
    val type: String,
    val state: String,
    val location: String,
    val organization: String,
    /** Datum des Beginns als ISO-Tag (JJJJ-MM-TT), für Sortierung und Filter. */
    val startDay: String,
    val startText: String,
    /** Uhrzeit des Beginns, z. B. "14:00"; leer, wenn die Website keine nennt. */
    val startTime: String,
    val endText: String,
    val registrationDeadline: String?,
)

sealed interface TournamentOutcome {
    data class Ok(val tournaments: List<Tournament>) : TournamentOutcome
    data class Failed(val message: String) : TournamentOutcome
}

/** Turnierkalender der Website. Die Liste steht als JSON im Seiten-HTML, eine Antwort enthält alle Turniere. */
class TournamentRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {
    private val cache = TtlCache<String, List<Tournament>>(CACHE_MILLIS)

    suspend fun load(): TournamentOutcome {
        cache.get(KEY)?.let { return TournamentOutcome.Ok(it) }
        return try {
            val r = session.fetch(Request.Builder().url(baseUrl.resolve("/turnierwelt/kalender")!!).build())
            val list = TournamentParser.parse(r.body)
                ?: return TournamentOutcome.Failed("Der Turnierkalender war nicht lesbar.")
            cache.put(KEY, list)
            TournamentOutcome.Ok(list)
        } catch (e: java.io.IOException) {
            TournamentOutcome.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }
    }

    private companion object {
        const val KEY = "kalender"
        /** Der Kalender ändert sich selten; 30 Minuten reichen. */
        const val CACHE_MILLIS = 30 * 60 * 1000L
    }
}

object TournamentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val MARKER = "\"tournaments\":["

    /** Liest die Liste aus dem Seiten-HTML; null, wenn die Liste nicht gefunden wird. */
    fun parse(html: String): List<Tournament>? {
        val start = html.indexOf(MARKER)
        if (start < 0) return null
        val arrStart = start + MARKER.length - 1
        val arrEnd = matchingBracket(html, arrStart) ?: return null
        val array = try {
            json.parseToJsonElement(html.substring(arrStart, arrEnd + 1)) as? JsonArray
        } catch (e: Exception) {
            null
        } ?: return null
        return array.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            Tournament(
                id = id,
                name = name,
                host = o.str("host").orEmpty(),
                type = o.str("type").orEmpty(),
                state = o.str("state").orEmpty(),
                location = o.str("location").orEmpty(),
                organization = o.str("organization_short").orEmpty(),
                startDay = o.str("start_date_time")?.take(10).orEmpty(),
                startText = o.str("formattedStartDate").orEmpty(),
                startTime = o.str("formattedStartTime").orEmpty(),
                endText = o.str("formattedEndDate").orEmpty(),
                registrationDeadline = o.str("registration_deadline"),
            )
        }
    }

    /** Index der schließenden eckigen Klammer zur Klammer bei [open], Zeichenketten werden übersprungen. */
    private fun matchingBracket(s: String, open: Int): Int? {
        var depth = 0
        var inString = false
        var i = open
        while (i < s.length) {
            val c = s[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}
