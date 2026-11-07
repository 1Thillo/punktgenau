package app.punktgenau.data

import app.punktgenau.data.session.AuthDetector
import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Request
import java.time.LocalDate

data class TeamInfo(val id: String, val name: String, val league: String?)
data class Appointment(val date: String?, val opponentTeamId: String, val opponentName: String)
data class TeamMember(val firstName: String, val lastName: String, val nuId: String?, val rank: Int?) {
    val displayName get() = "$firstName $lastName".trim()
}

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data object NeedsLogin : Result<Nothing>
    data class Failed(val message: String) : Result<Nothing>
}

/** Saisonbezeichnung wie "26/27"; Wechsel zum 1. Juli (, noch am echten Konto zu prüfen). */
object Season {
    fun label(today: LocalDate): String {
        val start = if (today.monthValue >= 7) today.year else today.year - 1
        return "%02d/%02d".format(start % 100, (start + 1) % 100)
    }
}

/** Mannschaften, Termine und Meldelisten ( und 3.4). */
class TeamRepository(
    private val session: SessionManager,
    private val baseUrl: HttpUrl,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val clubNr = Regex("\"clubnr\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val fedNickname = Regex("\"fedNickname\"\\s*:\\s*\"([^\"]*)\"")

    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: java.io.IOException) {
            Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }

    suspend fun myTeams(): Result<List<TeamInfo>> = guarded {
        val home = session.fetch(Request.Builder().url(baseUrl.resolve("/community?show=everything")!!).build())
        if (AuthDetector.isUnauthenticated(home.body)) return@guarded Result.NeedsLogin
        val club = clubNr.find(home.body)?.groupValues?.get(1)
        val fed = fedNickname.find(home.body)?.groupValues?.get(1)
        if (club.isNullOrBlank() || fed.isNullOrBlank()) {
            return@guarded Result.Failed("Verein oder Verband wurden auf der Startseite nicht gefunden.")
        }
        val url = baseUrl.newBuilder().encodedPath("/api/ttr/teams")
            .addQueryParameter("clubNumber", club).addQueryParameter("organization", fed).build()
        val r = session.fetch(Request.Builder().url(url).build())
        if (AuthDetector.isUnauthenticated(r.body)) return@guarded Result.NeedsLogin
        val teams = TeamParser.parseTeams(r.body, Season.label(today()))
            ?: return@guarded Result.Failed("Die Mannschaftsliste war nicht lesbar.")
        Result.Ok(teams)
    }

    suspend fun schedule(teamId: String): Result<List<Appointment>> = guarded {
        val url = baseUrl.newBuilder().encodedPath("/api/ttr/team/schedule")
            .addQueryParameter("teamId", teamId).addQueryParameter("season", Season.label(today())).build()
        val r = session.fetch(Request.Builder().url(url).build())
        if (AuthDetector.isUnauthenticated(r.body)) return@guarded Result.NeedsLogin
        val list = TeamParser.parseSchedule(r.body) ?: return@guarded Result.Failed("Die Termine waren nicht lesbar.")
        Result.Ok(list)
    }

    suspend fun players(teamId: String): Result<List<TeamMember>> = guarded {
        val url = baseUrl.newBuilder().encodedPath("/api/ttr/team/players").addQueryParameter("teamId", teamId).build()
        val r = session.fetch(Request.Builder().url(url).build())
        if (AuthDetector.isUnauthenticated(r.body)) return@guarded Result.NeedsLogin
        val list = TeamParser.parsePlayers(r.body) ?: return@guarded Result.Failed("Die Meldeliste war nicht lesbar.")
        Result.Ok(list)
    }
}

object TeamParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun dataList(body: String): List<JsonObject>? {
        val root = try {
            json.parseToJsonElement(body)
        } catch (e: Exception) {
            return null
        }
        val arr = (root as? JsonObject)?.get("data") as? JsonArray ?: return null
        return arr.mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    /** Bevorzugt Mannschaften der laufenden Saison; gibt es keine, alle. */
    fun parseTeams(body: String, season: String): List<TeamInfo>? {
        val items = dataList(body) ?: return null
        val inSeason = items.filter { it.str("season") == season }.ifEmpty { items }
        return inSeason.mapNotNull { o ->
            val id = o.str("team_id") ?: return@mapNotNull null
            TeamInfo(id, o.str("team_name") ?: "Mannschaft $id", o.str("league_name") ?: o.str("league"))
        }
    }

    fun parseSchedule(body: String): List<Appointment>? =
        dataList(body)?.mapNotNull { o ->
            val id = o.str("opponent_team_id") ?: return@mapNotNull null
            Appointment(o.str("date"), id, o.str("opponent_team_name") ?: "Gegner")
        }?.sortedBy { it.date }

    fun parsePlayers(body: String): List<TeamMember>? =
        dataList(body)?.map { o ->
            TeamMember(o.str("firstname").orEmpty(), o.str("lastname").orEmpty(), o.str("internal_id"), o.str("rank")?.toIntOrNull())
        }?.sortedBy { it.rank ?: Int.MAX_VALUE }
}
