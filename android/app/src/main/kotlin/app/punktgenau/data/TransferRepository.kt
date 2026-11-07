package app.punktgenau.data

import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Request

/** Ein Spielerwechsel (Lizenzwechsel) mit Alt- und Neuverein. */
data class Transfer(
    val name: String,
    val gender: String?,
    val oldClub: String,
    val newClub: String,
    val parts: List<String>,
)

/** Lizenzzeitraum der Wechselliste; [from] ist der Parameter für die Abfrage. */
data class TransferPeriod(val label: String, val from: String)

data class TransferPage(val periods: List<TransferPeriod>, val transfers: List<Transfer>)

/** Wechselliste des Verbands (öffentliche click-tt-Seite, Remix-JSON). */
class TransferRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    private val route = "routes/click-tt+/\$association.wechselliste"

    suspend fun load(association: String, incoming: Boolean, periodFrom: String?, query: String): Result<TransferPage> = try {
        val url = baseUrl.newBuilder()
            .addPathSegments("click-tt/$association/wechselliste")
            .addQueryParameter("_data", route)
            .addQueryParameter("transfer-direction", if (incoming) "incoming" else "outgoing")
            .apply {
                if (!periodFrom.isNullOrBlank()) addQueryParameter("license-period", periodFrom)
                if (query.isNotBlank()) addQueryParameter("player-query", query.trim())
            }
            .build()
        val body = session.fetch(Request.Builder().url(url).build()).body
        TransferParser.page(body)?.let { Result.Ok(it) } ?: Result.Failed("Die Wechselliste war nicht lesbar.")
    } catch (e: java.io.IOException) {
        Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
    }
}

object TransferParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    fun page(body: String): TransferPage? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val data = (root["data"] as? JsonObject) ?: root
        val list = data["playerTransfers"] as? JsonArray ?: return null
        val transfers = list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = listOfNotNull(o.str("firstname"), o.str("lastname")).joinToString(" ")
            if (name.isBlank()) return@mapNotNull null
            Transfer(
                name = name,
                gender = o.str("gender"),
                oldClub = o.str("old_club_name") ?: "",
                newClub = o.str("new_club_name") ?: "",
                parts = (o["licence_transfer_part"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            )
        }
        val periods = (data["licensePeriods"] as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val from = o.str("from") ?: return@mapNotNull null
            TransferPeriod(o.str("label") ?: from, from)
        }
        return TransferPage(periods, transfers)
    }
}
