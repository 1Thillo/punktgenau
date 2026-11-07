package app.punktgenau.data

import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Request

/** Filter der erweiterten Suche; Werte wie auf der Ranglistenseite (andro-Rangliste). */
data class SearchFilter(
    val clubNr: String = "",
    val fedNickname: String = "RTTVR",
    /** "all", "m" oder "w". */
    val gender: String = "all",
    /** true: aktuelle TTR, false: Quartals-Q-TTR. */
    val currentTtr: Boolean = true,
    val ttrMin: Int = 100,
    val ttrMax: Int = 3000,
    val birthFrom: Int = 1925,
    val birthTo: Int = 2020,
)

/** Ein Treffer der Rangliste; [nuId] ist die Kennung für den TTR-Abruf. */
data class RankedPlayer(
    val nuId: String?,
    val firstName: String,
    val lastName: String,
    val club: String?,
    val rank: Int?,
)

data class SearchPage(val players: List<RankedPlayer>, val pages: Int)

/** Erweiterte Spielersuche über die öffentliche Ranglistenseite. */
class AdvancedSearchRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    suspend fun search(filter: SearchFilter, page: Int): Result<SearchPage> = try {
        val url = baseUrl.newBuilder()
            .addPathSegments("rankings/andro-rangliste")
            .addQueryParameter("continent", "all")
            .addQueryParameter("country", "all")
            .addQueryParameter("all-players", "on")
            .addQueryParameter("as", "all")
            .addQueryParameter("fednickname", filter.fedNickname)
            .addQueryParameter("gender", filter.gender)
            .addQueryParameter("current-ranking", if (filter.currentTtr) "yes" else "no")
            .addQueryParameter("ttr-range", "${filter.ttrMin};${filter.ttrMax}")
            .addQueryParameter("birth-range", "${filter.birthFrom};${filter.birthTo}")
            .addQueryParameter("results-per-page", "20")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_data", "routes/\$")
            .apply { if (filter.clubNr.isNotBlank()) addQueryParameter("clubnr", filter.clubNr.trim()) }
            .build()
        val body = session.fetch(Request.Builder().url(url).build()).body
        AdvancedSearchParser.page(body)?.let { Result.Ok(it) } ?: Result.Failed("Die Suche war nicht lesbar.")
    } catch (e: java.io.IOException) {
        Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
    }
}

object AdvancedSearchParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.int(k: String): Int? = str(k)?.toDoubleOrNull()?.toInt()

    /** Sucht den Block mit `entries` und `resultLength`, egal wo er in der Antwort steht. */
    private fun findBlock(e: JsonElement): JsonObject? = when (e) {
        is JsonObject -> if (e.containsKey("entries") && e.containsKey("resultLength")) e
        else e.values.firstNotNullOfOrNull { findBlock(it) }
        is JsonArray -> e.firstNotNullOfOrNull { findBlock(it) }
        else -> null
    }

    fun page(body: String): SearchPage? {
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return null
        }
        val block = findBlock(root) ?: return null
        val entries = block["entries"] as? JsonArray ?: return null
        val players = entries.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val first = o.str("firstname").orEmpty()
            val last = o.str("lastname").orEmpty()
            if (first.isBlank() && last.isBlank()) return@mapNotNull null
            RankedPlayer(
                nuId = o.str("personId"),
                firstName = first,
                lastName = last,
                club = o.str("clubName"),
                rank = o.int("position"),
            )
        }
        return SearchPage(players, block.int("numberOfPages") ?: 1)
    }
}
