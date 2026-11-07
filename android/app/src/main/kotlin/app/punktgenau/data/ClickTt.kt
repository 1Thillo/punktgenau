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

/** Verweis auf eine Gruppe (Liga) einer Saison, z. B. "2. Kreisklasse Gr. B". */
data class GroupRef(val association: String, val season: String, val groupId: String, val name: String)

data class District(val name: String, val championship: String)
data class LeagueGroup(val name: String, val groupId: String, val category: String)

data class TableRow(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val meetings: Int,
    val won: Int,
    val tie: Int,
    val lost: Int,
    val pointsWon: Int,
    val pointsLost: Int,
    val matchesWon: Int,
    val matchesLost: Int,
    val trend: String,
)

data class LeagueTable(val rows: List<TableRow>, val ttrAverage: Int?)

data class GroupMeeting(
    val date: String?,
    val home: String,
    val away: String,
    val homeId: String?,
    val awayId: String?,
    val result: String?,
)

data class BalanceRow(
    val rank: Int,
    val playerId: String,
    val name: String,
    val team: String,
    val teamId: String,
    val rating: String,
    val won: Int,
    val lost: Int,
    val positions: List<Pair<Int, String>>,
    val teamRank: Int?,
)

data class OpponentLine(val opponentRank: Int, val won: Int, val lost: Int)

/** Spieler einer Mannschaft mit Einsätzen und Bilanz je gegnerischer Position. */
data class PlayerLine(
    val playerId: String,
    val name: String,
    val position: String,
    val appearances: Int,
    val won: Int,
    val lost: Int,
    val perOpponent: List<OpponentLine>,
)

private const val PREFIX = "routes/click-tt+/\$association+/\$season+/\$type+/"
private const val ROUTE_ASSOCIATION = PREFIX + "_index"
private const val ROUTE_CHAMPIONSHIP = PREFIX + "\$championship"
private const val GROUP = PREFIX + "\$groupname.gruppe.\$urlid+/"
private const val TEAM = PREFIX + "(\$groupname).gruppe.\$urlid_.mannschaft.\$teamid.\$teamname+/"

/** Öffentliche click-tt-Seiten der Website (Remix-Loader als JSON). Die Namensteile der Adressen sind beliebig. */
class ClickTtRepository(private val session: SessionManager, private val baseUrl: HttpUrl) {

    private suspend fun <T> load(path: String, route: String, parse: (JsonObject) -> T?): Result<T> =
        try {
            val url = baseUrl.newBuilder().addPathSegments(path.trimStart('/')).addQueryParameter("_data", route).build()
            val r = session.fetch(Request.Builder().url(url).build())
            val root = ClickTtParser.root(r.body)
            val value = root?.let(parse)
            if (value == null) Result.Failed("Die Seite war nicht lesbar.") else Result.Ok(value)
        } catch (e: java.io.IOException) {
            Result.Failed("Netzwerkfehler. Bitte später erneut versuchen.")
        }

    private fun seasonPath(season: String) = season.replace("/", "--")
    private fun groupPath(g: GroupRef) = "click-tt/${g.association}/${seasonPath(g.season)}/ligen/x/gruppe/${g.groupId}"

    suspend fun districts(association: String, season: String): Result<List<District>> =
        load("click-tt/$association/${seasonPath(season)}/ligen", ROUTE_ASSOCIATION, ClickTtParser::districts)

    suspend fun groups(association: String, season: String, district: District): Result<List<LeagueGroup>> {
        val champ = district.championship.replace("/", "--").replace(" ", "_")
        return load("click-tt/$association/${seasonPath(season)}/ligen/$champ", ROUTE_CHAMPIONSHIP, ClickTtParser::groups)
    }

    suspend fun table(g: GroupRef, filter: String = "gesamt"): Result<LeagueTable> =
        load("${groupPath(g)}/tabelle/$filter", GROUP + "tabelle.\$filter", ClickTtParser::table)

    suspend fun schedule(g: GroupRef, filter: String = "gesamt"): Result<List<GroupMeeting>> =
        load("${groupPath(g)}/spielplan/$filter", GROUP + "spielplan.\$filter", ClickTtParser::meetings)

    suspend fun ranking(g: GroupRef, filter: String = "gesamt"): Result<List<BalanceRow>> =
        load("${groupPath(g)}/gruppen-ranglisten/spieler/$filter", GROUP + "gruppen-ranglisten.\$match_type.\$filter", ClickTtParser::ranking)

    suspend fun teamPlayers(g: GroupRef, teamId: String, filter: String = "gesamt"): Result<List<PlayerLine>> =
        load("${groupPath(g)}/mannschaft/$teamId/x/spielerbilanzen/$filter", TEAM + "spielerbilanzen.\$filter", ClickTtParser::teamPlayers)
}

object ClickTtParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun root(body: String): JsonObject? = try {
        json.parseToJsonElement(body) as? JsonObject
    } catch (e: Exception) {
        null
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = this as? JsonArray
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(k: String): Int = str(k)?.toDoubleOrNull()?.toInt() ?: 0

    fun districts(root: JsonObject): List<District>? =
        root["data"].obj()?.get("districts").arr()?.mapNotNull { d ->
            val o = d.obj() ?: return@mapNotNull null
            District(o.str("name") ?: return@mapNotNull null, o.str("championship") ?: return@mapNotNull null)
        }

    fun groups(root: JsonObject): List<LeagueGroup>? =
        root["data"].arr()?.flatMap { c ->
            val o = c.obj() ?: return@flatMap emptyList()
            val category = o.str("contest") ?: ""
            o["groups"].arr().orEmpty().mapNotNull { g ->
                val go = g.obj() ?: return@mapNotNull null
                LeagueGroup(go.str("name") ?: return@mapNotNull null, go.str("group_urlid") ?: return@mapNotNull null, category)
            }
        }

    fun table(root: JsonObject): LeagueTable? {
        val data = root["data"].obj() ?: return null
        val rows = data["league_table"].arr()?.mapNotNull { r ->
            val o = r.obj() ?: return@mapNotNull null
            TableRow(
                rank = o.int("table_rank"),
                teamId = o.str("team_id") ?: "",
                teamName = o.str("team_name") ?: return@mapNotNull null,
                meetings = o.int("meetings_count"),
                won = o.int("meetings_won"),
                tie = o.int("meetings_tie"),
                lost = o.int("meetings_lost"),
                pointsWon = o.int("points_won"),
                pointsLost = o.int("points_lost"),
                matchesWon = o.int("matches_won"),
                matchesLost = o.int("matches_lost"),
                trend = o.str("rise_fall_state") ?: "none",
            )
        } ?: return null
        return LeagueTable(rows, data.str("league_ttr_avg")?.toIntOrNull())
    }

    /** `data.meetings`: Liste von Objekten Datum -> Spiele. */
    fun meetings(root: JsonObject): List<GroupMeeting>? {
        val days = root["data"].obj()?.get("meetings").arr() ?: return null
        val out = ArrayList<GroupMeeting>()
        for (day in days) {
            for (list in day.obj()?.values.orEmpty()) {
                for (m in list.arr().orEmpty()) {
                    val o = m.obj() ?: continue
                    val won = o.int("matches_won")
                    val lost = o.int("matches_lost")
                    out.add(
                        GroupMeeting(
                            o.str("date"), o.str("team_home") ?: continue, o.str("team_away") ?: continue,
                            o.str("team_home_id"), o.str("team_away_id"),
                            if (won + lost > 0) "$won:$lost" else null,
                        ),
                    )
                }
            }
        }
        return out.sortedBy { it.date }
    }

    fun ranking(root: JsonObject): List<BalanceRow>? =
        root["data"].obj()?.get("ranking").arr()?.mapNotNull { r ->
            val o = r.obj() ?: return@mapNotNull null
            BalanceRow(
                rank = o.int("player_rank"),
                playerId = o.str("player_id") ?: "",
                name = listOfNotNull(o.str("player_firstname"), o.str("player_lastname")).joinToString(" "),
                team = o.str("team_name") ?: "",
                teamId = o.str("team_id") ?: "",
                rating = o.str("player_rating") ?: "",
                won = o.int("player_matches_won"),
                lost = o.int("player_matches_lost"),
                positions = o["player_position_statistics"].arr().orEmpty().mapNotNull { p ->
                    val po = p.obj() ?: return@mapNotNull null
                    po.int("position") to "${po.int("position_matches_won")}:${po.int("position_matches_lost")}"
                },
                teamRank = o.str("player_team_rank")?.toIntOrNull(),
            )
        }

    /** Erste Mannschaft aus `data.balancesheet`: Einzelspieler mit Einsätzen. */
    fun teamPlayers(root: JsonObject): List<PlayerLine>? {
        val sheet = root["data"].obj()?.get("balancesheet").arr()?.firstOrNull().obj() ?: return null
        return sheet["single_player_statistics"].arr()?.mapNotNull { p ->
            val o = p.obj() ?: return@mapNotNull null
            val teamNr = o.str("team_number")
            val rank = o.str("player_rank")
            PlayerLine(
                playerId = o.str("player_id") ?: "",
                name = listOfNotNull(o.str("player_firstname"), o.str("player_lastname")).joinToString(" "),
                position = if (teamNr != null && rank != null) "$teamNr.$rank" else rank ?: "",
                appearances = o.int("meeting_count"),
                won = o.int("points_won"),
                lost = o.int("points_lost"),
                perOpponent = o["single_statistics"].arr().orEmpty().mapNotNull { s ->
                    val so = s.obj() ?: return@mapNotNull null
                    OpponentLine(so.int("opponent_rank"), so.int("points_won"), so.int("points_lost"))
                },
            )
        }
    }
}
