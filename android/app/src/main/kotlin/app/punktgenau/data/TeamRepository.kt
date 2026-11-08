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

data class TeamInfo(
    val id: String,
    val name: String,
    val league: String?,
    val season: String? = null,
    val groupId: String? = null,
    val organization: String? = null,
    /** Vereinsname, nur bei gemerkten Mannschaften bekannt; sonst null. */
    val club: String? = null,
)

/** Ein Mannschaftsspiel aus dem Spielplan. [result] steht nur bei gespielten Spielen (Heim:Gast). */
data class Appointment(
    val date: String?,
    val opponentTeamId: String,
    val opponentName: String,
    val home: Boolean? = null,
    val result: String? = null,
    /** Spielbericht-Kennung (meeting_id) und Liga-Kennung (league_id) aus dem Spielplan. */
    val meetingId: String? = null,
    val leagueId: String? = null,
    /** Name der eigenen Mannschaft wie im Spielplan (Verein + Mannschaft), sofern vorhanden. */
    val ownName: String? = null,
    /** Laufendes Spiel laut Spielplan (Feld `live`); nur dann lohnt sich eine Aktualisierung. */
    val live: Boolean = false,
)

/** Ansicht des Spielplans: gesamt, Vorrunde, Rückrunde (wie auf der Website). */
enum class ScheduleFilter(val path: String) { ALL("gesamt"), FIRST_HALF("vr"), SECOND_HALF("rr") }
data class TeamMember(val firstName: String, val lastName: String, val nuId: String?, val rank: Int?) {
    val displayName get() = "$firstName $lastName".trim()
}

sealed interface Result<out T> {
    data class Ok<T>(val value: T) : Result<T>
    data object NeedsLogin : Result<Nothing>
    data class Failed(val message: String) : Result<Nothing>
}

/** Saisonbezeichnung wie "26/27"; Wechsel zum 1. Juli (noch am echten Konto zu prüfen). */
object Season {
    fun label(today: LocalDate): String {
        val start = if (today.monthValue >= 7) today.year else today.year - 1
        return "%02d/%02d".format(start % 100, (start + 1) % 100)
    }
}

/** Mannschaften, Termine und Meldelisten. */
private const val SCHEDULE_ROUTE =
    "routes/click-tt+/\$association+/\$season+/\$type+/(\$groupname).gruppe.\$urlid_.mannschaft.\$teamid.\$teamname+/spielplan.\$filter"

class TeamRepository(
    private val session: SessionManager,
    private val baseUrl: HttpUrl,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val clubNr = Regex("\"clubnr\"\\s*:\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val fedNickname = Regex("\"fedNickname\"\\s*:\\s*\"([^\"]*)\"")
    private val scheduleCache = TtlCache<String, List<Appointment>>(10 * 60 * 1000L)
    private val playersCache = TtlCache<String, List<TeamMember>>(30 * 60 * 1000L)

    private suspend fun <T> guarded(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: java.io.IOException) {
            Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }

    /** Vereinsnummer aus der Startseite (für die Vereinsseite). */
    suspend fun ownClubNr(): String? = try {
        val home = session.fetch(Request.Builder().url(baseUrl.resolve("/community?show=everything")!!).build())
        clubNr.find(home.body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    } catch (e: java.io.IOException) {
        null
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

    /**
     * Spielplan einer Mannschaft von den click-tt-Seiten der Website (öffentlich, gleiche Daten wie die Ansicht
     * `.../mannschaft/<ID>/.../spielplan/gesamt|vr|rr`). Die Namensteile der Adresse sind beliebig.
     */
    suspend fun schedule(team: TeamInfo, filter: ScheduleFilter, fresh: Boolean = false): Result<List<Appointment>> = guarded {
        val org = team.organization
        val group = team.groupId
        val season = team.season
        if (org == null || group == null || season == null) {
            return@guarded Result.Failed("Für diese Mannschaft fehlen Verband, Gruppe oder Saison.")
        }
        val cacheKey = "${team.id}|$season|${filter.path}"
        if (!fresh) scheduleCache.get(cacheKey)?.let { return@guarded Result.Ok(it) }
        val url = baseUrl.newBuilder()
            .addPathSegments("click-tt/$org/${season.replace("/", "--")}/ligen/x/gruppe/$group/mannschaft/${team.id}/x/spielplan/${filter.path}")
            .addQueryParameter("_data", SCHEDULE_ROUTE)
            .build()
        val r = session.fetch(Request.Builder().url(url).build())
        if (AuthDetector.isUnauthenticated(r.body)) return@guarded Result.NeedsLogin
        val list = TeamParser.parseSchedule(r.body, team.id)
            ?: return@guarded Result.Failed("Der Spielplan war nicht lesbar.")
        scheduleCache.put(cacheKey, list)
        Result.Ok(list)
    }

    suspend fun players(teamId: String): Result<List<TeamMember>> = guarded {
        playersCache.get(teamId)?.let { return@guarded Result.Ok(it) }
        val url = baseUrl.newBuilder().encodedPath("/api/ttr/team/players").addQueryParameter("teamId", teamId).build()
        val r = session.fetch(Request.Builder().url(url).build())
        if (AuthDetector.isUnauthenticated(r.body)) return@guarded Result.NeedsLogin
        val list = TeamParser.parsePlayers(r.body) ?: return@guarded Result.Failed("Die Meldeliste war nicht lesbar.")
        playersCache.put(teamId, list)
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
            TeamInfo(
                id, o.str("team_name") ?: "Mannschaft $id", o.str("league_name") ?: o.str("league"),
                o.str("season"), o.str("group_id"), o.str("team_organisation_short"),
            )
        }
    }

    /**
     * Spielplan aus der Antwort der click-tt-Seite: `data.meetings_excerpt.meetings` ist eine Liste von
     * Objekten Datum -> Liste von Spielen. Null, wenn der Aufbau nicht passt.
     */
    fun parseSchedule(body: String, ownTeamId: String): List<Appointment>? {
        val root = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val days = ((root["data"] as? JsonObject)?.get("meetings_excerpt") as? JsonObject)?.get("meetings") as? JsonArray
            ?: return null
        val out = ArrayList<Appointment>()
        for (day in days) {
            for (list in (day as? JsonObject)?.values.orEmpty()) {
                for (m in (list as? JsonArray).orEmpty()) {
                    val o = m as? JsonObject ?: continue
                    val homeId = o.str("team_home_id")
                    val awayId = o.str("team_away_id")
                    val isHome = when (ownTeamId) {
                        homeId -> true
                        awayId -> false
                        else -> continue
                    }
                    val oppId = (if (isHome) awayId else homeId) ?: continue
                    val oppName = o.str(if (isHome) "team_away" else "team_home") ?: "Gegner"
                    val ownName = o.str(if (isHome) "team_home" else "team_away")
                    val won = o.str("matches_won")?.toIntOrNull() ?: 0
                    val lost = o.str("matches_lost")?.toIntOrNull() ?: 0
                    val result = if (won + lost > 0) "$won:$lost" else null
                    out.add(
                        Appointment(
                            o.str("date"), oppId, oppName, isHome, result, o.str("meeting_id"), o.str("league_id"), ownName,
                            live = isLive(o),
                        ),
                    )
                }
            }
        }
        return out.sortedBy { it.date }
    }

    /**
     * Laufendes Spiel: Feld `live` oder `status` aus dem Spielplan. Format nicht am echten Konto geprüft,
     * daher tolerant: "0", "false", "no" und fehlende Werte zählen als nicht laufend.
     */
    private fun isLive(o: JsonObject): Boolean {
        val live = o.str("live")?.lowercase()
        if (live != null) return live !in setOf("0", "false", "no")
        return o.str("status")?.lowercase() == "live"
    }

    fun parsePlayers(body: String): List<TeamMember>? =
        dataList(body)?.map { o ->
            TeamMember(o.str("firstname").orEmpty(), o.str("lastname").orEmpty(), o.str("internal_id"), o.str("rank")?.toIntOrNull())
        }?.sortedBy { it.rank ?: Int.MAX_VALUE }
}
