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

/** Adresse eines Spiellokals; [query] ist der Text für die Navigation. */
data class Venue(val name: String, val street: String, val zip: String, val city: String) {
    val query: String
        get() = listOf(street, listOf(zip, city).filter { it.isNotBlank() }.joinToString(" ")).filter { it.isNotBlank() }.joinToString(", ")
}

data class TeamContact(val name: String?, val phone: String?, val email: String?)

/** Mannschaftskontakt und die Heimspiellokale der Mannschaft (aus dem Spielplan der Mannschaft). */
data class TeamDetails(val contact: TeamContact?, val homeVenues: List<Venue>)

data class ClubInfo(
    val name: String,
    val region: String?,
    val contactName: String?,
    val contactPhone: String?,
    val contactEmail: String?,
    val venues: List<Venue> = emptyList(),
)

data class Club(val info: ClubInfo?, val teams: List<ClubTeam>)

/** Vereinsnummer und Verband (z. B. RTTVR) eines Vereins. */
data class ClubRef(val clubNr: String, val association: String)

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

    /** Vereinsnummer und Verband zu einem Vereinsnamen, über die Vereinssuche; nur ein genauer Treffer zählt. */
    suspend fun findClub(name: String): ClubRef? = try {
        val body = okhttp3.FormBody.Builder().add("query", name.trim()).build()
        val r = session.fetch(Request.Builder().url(baseUrl.resolve("/api/search/clubs")!!).post(body).build())
        ClubParser.clubRef(r.body, name)
    } catch (e: java.io.IOException) {
        null
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
        val infos = data["club_infos"] as? JsonObject
        val contacts = (infos?.get("club_contacts") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val official = (infos?.get("club_officials") as? JsonArray)?.firstOrNull() as? JsonObject
        val contact = contacts.firstOrNull { it.str("label")?.startsWith("Kontakt") == true } ?: contacts.firstOrNull()
        return ClubInfo(
            name = data.str("club_name") ?: return null,
            region = data.str("region_name"),
            contactName = official?.str("contact_name") ?: contact?.str("contact_name"),
            contactPhone = official?.str("phone_mobile") ?: contact?.str("phone_home"),
            contactEmail = contact?.str("email_home"),
            venues = contacts.filter { it.str("label")?.startsWith("Spiellokal") == true }.mapNotNull { venue(it) },
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

    /** Genauer Treffer (Name ohne Groß-/Kleinschreibung) aus der Vereinssuche. */
    fun clubRef(body: String, name: String): ClubRef? {
        val results = root(body)?.get("results") as? JsonArray ?: return null
        return results.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val clubName = o.str("clubname") ?: return@mapNotNull null
            val nr = o.str("clubnr") ?: return@mapNotNull null
            if (!clubName.equals(name.trim(), ignoreCase = true)) return@mapNotNull null
            ClubRef(nr, o.str("organization_short") ?: "RTTVR")
        }.firstOrNull()
    }

    /** Adresse aus einem Kontakt- oder Ortsobjekt (Felder street, zipcode/zip, city, label/contact_name). */
    fun venue(o: JsonObject): Venue? {
        val street = o.str("street").orEmpty()
        val city = o.str("city").orEmpty()
        if (street.isBlank() && city.isBlank()) return null
        return Venue(
            name = o.str("contact_name") ?: o.str("label") ?: "",
            street = street,
            zip = o.str("zipcode") ?: o.str("zip") ?: "",
            city = city,
        )
    }
}
