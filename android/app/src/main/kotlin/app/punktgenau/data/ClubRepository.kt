package app.punktgenau.data

import app.punktgenau.data.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.Request

data class ClubTeam(
    val teamId: String,
    val name: String,
    val league: String,
    val groupId: String,
    val season: String,
    val organization: String,
    val rank: String?,
)

data class ClubInfo(val name: String, val region: String?, val contactName: String?, val contactPhone: String?, val contactEmail: String?)

data class Club(val info: ClubInfo?, val teams: List<ClubTeam>)

/** Verein: Stammdaten und Mannschaften (öffentliche click-tt-Seiten, Remix-JSON). */
class ClubRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    private val infoRoute = "routes/click-tt+/\$association+/\$season+/verein.\$clubid.\$clubname+/info"
    private val teamsRoute = "routes/click-tt+/\$association+/\$season+/verein.\$clubid.\$clubname+/mannschaften"

    suspend fun load(clubNr: String, association: String, season: String): Result<Club> = try {
        val base = "click-tt/$association/${season.replace("/", "--")}/verein/$clubNr/x"
        val infoBody = session.fetch(Request.Builder().url(page(base, "info", infoRoute)).build()).body
        val teamsBody = session.fetch(Request.Builder().url(page(base, "mannschaften", teamsRoute)).build()).body
        val info = ClubParser.info(infoBody)
        val teams = ClubParser.teams(teamsBody) ?: return@load Result.Failed("Die Mannschaften des Vereins waren nicht lesbar.")
        if (info == null && teams.isEmpty()) Result.Failed("Keine Vereinsdaten gefunden.") else Result.Ok(Club(info, teams))
    } catch (e: java.io.IOException) {
        Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
    }

    private fun page(base: String, leaf: String, route: String): HttpUrl =
        baseUrl.newBuilder().addPathSegments("$base/$leaf").addQueryParameter("_data", route).build()
}

object ClubParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" && it.trim().isNotEmpty() }

    private fun root(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (e: Exception) {
        null
    }

    fun info(body: String): ClubInfo? {
        val data = root(body)?.get("data") as? JsonObject ?: return null
        val contact = ((data["club_infos"] as? JsonObject)?.get("club_contacts") as? JsonArray)
            ?.firstOrNull() as? JsonObject
        val official = ((data["club_infos"] as? JsonObject)?.get("club_officials") as? JsonArray)
            ?.firstOrNull() as? JsonObject
        return ClubInfo(
            name = data.str("club_name") ?: return null,
            region = data.str("region_name"),
            contactName = official?.str("contact_name") ?: contact?.str("contact_name"),
            contactPhone = official?.str("phone_mobile") ?: contact?.str("phone_home"),
            contactEmail = contact?.str("email_home"),
        )
    }

    fun teams(body: String): List<ClubTeam>? {
        val list = ((root(body)?.get("data") as? JsonObject)?.get("teams_list") as? JsonObject)
            ?.get("club_teams") as? JsonArray ?: return null
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            ClubTeam(
                teamId = o.str("team_id") ?: return@mapNotNull null,
                name = o.str("team_name") ?: "",
                league = o.str("league_name") ?: "",
                groupId = o.str("group_id") ?: return@mapNotNull null,
                season = o.str("season") ?: "",
                organization = o.str("team_organisation_short") ?: "",
                rank = o.str("table_rank"),
            )
        }
    }
}
