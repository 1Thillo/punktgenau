package app.punktgenau.data

import app.punktgenau.data.session.AuthDetector
import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

data class PlayerHit(val firstName: String, val lastName: String, val club: String?, val nuId: String?) {
    val displayName get() = "$firstName $lastName".trim()
}

sealed interface SearchOutcome {
    data class Hits(val players: List<PlayerHit>) : SearchOutcome
    data object NeedsLogin : SearchOutcome
    data class Failed(val message: String) : SearchOutcome
}

sealed interface RatingOutcome {
    /** [atDate] ist der Wert zum gewünschten Tag (falls der Verlauf ihn hergibt), sonst null. */
    data class Values(val ttr: Int?, val qttr: Int?, val atDate: Int? = null) : RatingOutcome
    data object NeedsLogin : RatingOutcome
    data class Failed(val message: String) : RatingOutcome
}

/** Spielersuche und Werte anderer Spieler (). Feldnamen noch am echten Konto zu prüfen. */
class PlayerRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    suspend fun search(query: String): SearchOutcome {
        if (query.isBlank()) return SearchOutcome.Hits(emptyList())
        return try {
            val body = FormBody.Builder().add("query", query.trim()).add("page", "1").add("pagesize", "40").build()
            val r = session.fetch(Request.Builder().url(baseUrl.resolve("/api/search/players")!!).post(body).build())
            if (AuthDetector.isUnauthenticated(r.body)) return SearchOutcome.NeedsLogin
            val hits = PlayerParser.parseSearch(r.body)
                ?: return SearchOutcome.Failed("Die Antwort der Suche war nicht lesbar.")
            SearchOutcome.Hits(hits)
        } catch (e: java.io.IOException) {
            SearchOutcome.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }
    }

    suspend fun ratings(nuId: String, day: java.time.LocalDate? = null): RatingOutcome {
        return try {
            val r = session.fetch(Request.Builder().url(baseUrl.resolve("/api/ttr/history/$nuId")!!).build())
            if (AuthDetector.isUnauthenticated(r.body)) return RatingOutcome.NeedsLogin
            val (ttr, q) = ProfileParser.parseHistory(r.body)
            RatingOutcome.Values(ttr, q, day?.let { ProfileParser.ratingOn(ProfileParser.parseEvents(r.body), it) })
        } catch (e: java.io.IOException) {
            RatingOutcome.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }
    }
}

object PlayerParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Liste unter `results`; null, wenn die Antwort keine erkennbare Liste enthält. */
    fun parseSearch(body: String): List<PlayerHit>? {
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return null
        }
        val list: JsonArray = when {
            root is JsonObject -> (root["results"] as? JsonArray) ?: return null
            root is JsonArray -> root.jsonArray
            else -> return null
        }
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            PlayerHit(
                firstName = o.str("firstname").orEmpty(),
                lastName = o.str("lastname").orEmpty(),
                club = o.str("club_name") ?: o.str("licence_club"),
                nuId = o.str("internal_id"),
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
}
