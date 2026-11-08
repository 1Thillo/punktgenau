package app.punktgenau.data

import app.punktgenau.data.session.AuthDetector
import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.Request

/** Eigene Werte. [ttr] ist nur gesetzt, wenn der Server ihn dem Konto liefert. */
data class HistoryEvent(
    val date: java.time.LocalDate,
    val before: Int,
    val after: Int,
    val type: String = "",
    val name: String = "",
    val dateTime: String = "",
    val delta: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val ak: Int? = null,
)

data class OwnProfile(
    val name: String?,
    val nuId: String?,
    val ttr: Int?,
    val qttr: Int?,
    val events: List<HistoryEvent> = emptyList(),
)

/** Ergebnis eines Abrufs: Daten oder ein verständlicher Grund. */
sealed interface Loaded {
    data class Ok(val profile: OwnProfile) : Loaded
    data object NeedsLogin : Loaded
    data class Failed(val message: String) : Loaded
}

/** Liest Startseite und TTR-Verlauf. Die Feldnamen sind noch am echten Konto zu prüfen. */
class ProfileRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    suspend fun load(): Loaded {
        return try {
            val home = session.fetch(Request.Builder().url(baseUrl.resolve("/community?show=everything")!!).build())
            if (AuthDetector.isUnauthenticated(home.body)) return Loaded.NeedsLogin
            val me = ProfileParser.parseStartPage(home.body)
            val nu = me.nuId ?: return Loaded.Failed("Die Spieler-ID wurde auf der Startseite nicht gefunden.")
            val hist = session.fetch(Request.Builder().url(baseUrl.resolve("/api/ttr/history/$nu")!!).build())
            if (AuthDetector.isUnauthenticated(hist.body)) return Loaded.NeedsLogin
            val values = ProfileParser.parseHistory(hist.body)
            Loaded.Ok(me.copy(ttr = values.first, qttr = values.second, events = ProfileParser.parseEvents(hist.body)))
        } catch (e: java.io.IOException) {
            Loaded.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }
    }
}

object ProfileParser {
    private val clickttId = Regex("\"clicktt_id\"\\s*:\\s*\"?(\\d+)\"?")
    private val firstName = Regex("\"firstname\"\\s*:\\s*\"([^\"]*)\"")
    private val lastName = Regex("\"lastname\"\\s*:\\s*\"([^\"]*)\"")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseStartPage(html: String): OwnProfile {
        val id = clickttId.find(html)?.groupValues?.get(1)?.let { "NU$it" }
        val name = listOfNotNull(firstName.find(html)?.groupValues?.get(1), lastName.find(html)?.groupValues?.get(1))
            .joinToString(" ").ifBlank { null }
        return OwnProfile(name, id, null, null)
    }

    /** Liefert (ttr, q-ttr). Fehlende Felder bleiben null. */
    fun parseHistory(body: String): Pair<Int?, Int?> {
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return null to null
        }
        return intField(obj, "ttr") to intField(obj, "vq_ttr")
    }

    /** Ereignisse aus dem Verlauf (Liste unter `event`), ohne lesbares Datum ausgelassen. */
    fun parseEvents(body: String): List<HistoryEvent> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return emptyList()
        }
        val arr = root["event"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val raw = (o["event_date_time"] as? JsonPrimitive)?.content.orEmpty()
            val date = raw.take(10)
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val before = intField(o, "ttr_before") ?: return@mapNotNull null
            val after = intField(o, "ttr_after") ?: before
            fun text(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
            HistoryEvent(
                date, before, after, text("type"), text("event_name"), raw,
                delta = intField(o, "ttr_delta") ?: (after - before),
                won = intField(o, "matches_won") ?: 0,
                lost = intField(o, "matches_lost") ?: 0,
                ak = intField(o, "alteration_constant"),
            )
        }.sortedBy { it.date }
    }

    /**
     * TTR, der am Spieltag galt: gibt es am Tag ein Ereignis, dessen Wert davor; sonst der Stand nach dem
     * letzten Ereignis **vor** dem Tag; gibt es keins,
     * der Wert vor dem frühesten Ereignis. Null, wenn der Verlauf keine Ereignisse enthält.
     */
    fun ratingOn(events: List<HistoryEvent>, day: java.time.LocalDate): Int? {
        if (events.isEmpty()) return null
        events.firstOrNull { it.date == day }?.let { return it.before } // Wert, mit dem das Spiel gewertet wurde
        return events.lastOrNull { it.date < day }?.after ?: events.first().before
    }

    private fun intField(obj: JsonObject, key: String): Int? {
        val p = obj[key] as? JsonPrimitive ?: return null
        return p.intOrNull ?: p.content.toDoubleOrNull()?.toInt()
    }
}
