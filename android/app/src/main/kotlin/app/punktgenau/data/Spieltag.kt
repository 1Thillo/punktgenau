package app.punktgenau.data

import app.punktgenau.data.Result
import java.time.LocalDate

/** Ein Spiel einer eigenen Mannschaft mit dem Termin aus deren Spielplan. */
data class SpieltagMatch(val team: TeamInfo, val appointment: Appointment) {
    val day: LocalDate? get() = appointment.date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val time: String? get() = appointment.date?.takeIf { it.length >= 16 }?.substring(11, 16)
}

/** Reine Hilfen für die Spieltag-Übersicht; ohne Netz testbar. */
object Spieltag {
    /** Die Spieltage (Datum), an denen mindestens ein Spiel ist, aufsteigend. */
    fun days(matches: List<SpieltagMatch>): List<LocalDate> = matches.mapNotNull { it.day }.distinct().sorted()

    /** Index des ersten Spieltags ab [today]; gibt es keinen mehr, der letzte; ohne Spieltage -1. */
    fun startIndex(days: List<LocalDate>, today: LocalDate): Int {
        if (days.isEmpty()) return -1
        val i = days.indexOfFirst { !it.isBefore(today) }
        return if (i >= 0) i else days.lastIndex
    }

    /** Adresse des Spielberichts auf der Website; die Namensteile der Adresse sind beliebig, die Kennungen zählen. */
    fun reportUrl(org: String, season: String, leagueId: String, meetingId: String): String =
        "https://www.mytischtennis.de/click-tt/$org/${season.replace("/", "--")}/ligen/x/gruppe/$leagueId/spielbericht/$meetingId/x"
}

/** Spieltag-Übersicht: Spiele der eigenen und gemerkten Mannschaften aus dem öffentlichen Spielplan. */
class SpieltagRepository(
    private val teams: TeamRepository,
    private val follow: FollowStore,
) {

    /** Alle Spiele der eigenen und der gemerkten Mannschaften der laufenden Saison. */
    suspend fun ownMatches(): Result<List<SpieltagMatch>> {
        val own = when (val r = teams.myTeams()) {
            is Result.Ok -> r.value
            Result.NeedsLogin -> return Result.NeedsLogin
            is Result.Failed -> return r
        }
        val mine = (own + follow.load()).distinctBy { it.id }
        val out = ArrayList<SpieltagMatch>()
        for (team in mine) {
            when (val s = teams.schedule(team, ScheduleFilter.ALL)) {
                is Result.Ok -> s.value.forEach { out.add(SpieltagMatch(team, it)) }
                Result.NeedsLogin -> return Result.NeedsLogin
                is Result.Failed -> return s
            }
        }
        return Result.Ok(out)
    }

    /**
     * Lädt nur die Spielpläne der Mannschaften neu, die gerade ein laufendes Spiel haben (am Cache vorbei).
     * Die übrigen Spiele bleiben unverändert und in ihrer Reihenfolge.
     */
    suspend fun refreshLive(current: List<SpieltagMatch>): Result<List<SpieltagMatch>> {
        val liveTeams = current.filter { it.appointment.live }.map { it.team.id }.toSet()
        if (liveTeams.isEmpty()) return Result.Ok(current)
        val fresh = HashMap<String, List<SpieltagMatch>>()
        for (team in current.map { it.team }.distinctBy { it.id }.filter { it.id in liveTeams }) {
            when (val s = teams.schedule(team, ScheduleFilter.ALL, fresh = true)) {
                is Result.Ok -> fresh[team.id] = s.value.map { SpieltagMatch(team, it) }
                Result.NeedsLogin -> return Result.NeedsLogin
                is Result.Failed -> return s
            }
        }
        val out = ArrayList<SpieltagMatch>()
        val placed = HashSet<String>()
        for (m in current) {
            if (m.team.id !in liveTeams) out.add(m)
            else if (placed.add(m.team.id)) out.addAll(fresh[m.team.id].orEmpty())
        }
        return Result.Ok(out)
    }
}

/** Abstand der Aktualisierung laufender Spiele in Millisekunden (nur solange der Spieltag offen ist). */
const val LIVE_INTERVAL_MS = 45_000L
