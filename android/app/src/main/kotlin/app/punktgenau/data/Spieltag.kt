package app.punktgenau.data

import app.punktgenau.data.Result
import app.punktgenau.data.SearchOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** Ein Spiel einer eigenen Mannschaft mit dem Termin aus deren Spielplan. */
data class SpieltagMatch(val team: TeamInfo, val appointment: Appointment) {
    val day: LocalDate? get() = appointment.date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val time: String? get() = appointment.date?.takeIf { it.length >= 16 }?.substring(11, 16)
}

/** Mittelwert der stärksten Spieler einer Meldeliste; [isTtr] ist false, wenn ein Q-TTR-Wert dabei ist. */
data class TeamAverage(val value: Int, val count: Int, val isTtr: Boolean, val lineup: Boolean = false)

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

    /** Namen der eigenen Seite (Heim oder Gast) aus den Einzeln eines Spielberichts; Doppel bleiben außen vor. */
    fun lineupNames(games: List<GameLine>, ownHome: Boolean): List<String> =
        games.filter { it.home.size == 1 && it.away.size == 1 }
            .map { if (ownHome) it.home.single() else it.away.single() }
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Mittelwert der [n] höchsten Werte, auf ganze Punkte gerundet. Leer → null. */
    fun topAverage(values: List<Int>, n: Int = 4): Int? {
        val top = values.sortedDescending().take(n)
        if (top.isEmpty()) return null
        return Math.round(top.average()).toInt()
    }
}

/**
 * Spieltag-Übersicht: Spiele der eigenen Mannschaften (öffentlicher Spielplan) und die Ø-TTR je Mannschaft
 * aus ihrer Meldeliste (öffentliche TTR-Werte; Abruf nur mit Anmeldung).
 */
/** Ø-TTR beider Seiten eines gespielten Spiels aus dem Spielbericht (Spieltagswerte der Spieler, die gespielt haben). */
data class LineupAverages(val own: TeamAverage?, val opponent: TeamAverage?)

class SpieltagRepository(
    private val teams: TeamRepository,
    private val players: PlayerRepository,
    private val follow: FollowStore,
    private val report: SpielberichtRepository,
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

    /** Ø-TTR der Mannschaft aus ihrer Meldeliste: die vier höchsten Werte; Q-TTR, wenn kein TTR da ist. */
    suspend fun teamAverage(teamId: String): Result<TeamAverage?> {
        val list = when (val p = teams.players(teamId)) {
            is Result.Ok -> p.value
            Result.NeedsLogin -> return Result.NeedsLogin
            is Result.Failed -> return p
        }
        val nuIds = list.mapNotNull { it.nuId }.distinct()
        if (nuIds.isEmpty()) return Result.Ok(null)
        val gate = Semaphore(5)
        val outcomes = coroutineScope {
            nuIds.map { nu -> async { gate.withPermit { players.ratings(nu) } } }.awaitAll()
        }
        if (outcomes.any { it == RatingOutcome.NeedsLogin }) return Result.NeedsLogin
        val values = outcomes.filterIsInstance<RatingOutcome.Values>()
            .mapNotNull { v -> (v.ttr ?: v.qttr)?.let { it to (v.ttr != null) } }
        return Result.Ok(average(values))
    }

    /**
     * Ø-TTR eines gespielten Spiels aus dem Spielbericht: die Spieler, die tatsächlich gespielt haben, mit ihrem
     * Wert zum Spieltag. Null, wenn kein Bericht oder keine Zuordnung der Namen möglich ist.
     */
    suspend fun lineup(match: SpieltagMatch): Result<LineupAverages?> {
        val a = match.appointment
        val meeting = a.meetingId
        val league = a.leagueId
        val day = match.day
        if (a.result == null || meeting == null || league == null || day == null) return Result.Ok(null)
        val org = match.team.organization ?: "RTTVR"
        val season = match.team.season ?: Season.label(LocalDate.now())
        val rep = when (val r = report.load(Spieltag.reportUrl(org, season, league, meeting))) {
            is Result.Ok -> r.value
            Result.NeedsLogin -> return Result.NeedsLogin
            is Result.Failed -> return Result.Ok(null)
        }
        val home = a.home == true
        val own = sideAverage(Spieltag.lineupNames(rep.games, home), match.team.id, a.ownName.orEmpty(), day)
        val opp = sideAverage(Spieltag.lineupNames(rep.games, !home), a.opponentTeamId, a.opponentName, day)
        return when {
            own is Result.NeedsLogin || opp is Result.NeedsLogin -> Result.NeedsLogin
            else -> Result.Ok(
                LineupAverages(
                    (own as? Result.Ok)?.value,
                    (opp as? Result.Ok)?.value,
                ),
            )
        }
    }

    /**
     * Ø einer Seite aus den Einzelspielern des Berichts. Wer in der Meldeliste der Mannschaft steht, wird dort
     * gefunden; sonst (Aushilfe aus einer anderen Mannschaft des Vereins) über die Spielersuche, mit Verein
     * [fullName] ("Verein Mannschaft") als Prüfung.
     */
    private suspend fun sideAverage(names: List<String>, teamId: String, fullName: String, day: LocalDate): Result<TeamAverage?> {
        val members = when (val p = teams.players(teamId)) {
            is Result.Ok -> p.value
            Result.NeedsLogin -> return Result.NeedsLogin
            is Result.Failed -> emptyList()
        }
        val nuIds = mutableListOf<String>()
        for (n in names) {
            val member = members.firstOrNull { it.displayName.equals(n, ignoreCase = true) }?.nuId
            if (member != null) {
                nuIds += member
                continue
            }
            when (val s = players.search(n)) {
                is SearchOutcome.Hits -> s.players
                    .firstOrNull { h ->
                        "${h.firstName} ${h.lastName}".equals(n, ignoreCase = true) &&
                            h.club != null && fullName.startsWith(h.club) && h.nuId != null
                    }
                    ?.nuId?.let { nuIds += it }
                SearchOutcome.NeedsLogin -> return Result.NeedsLogin
                is SearchOutcome.Failed -> Unit
            }
        }
        val ids = nuIds.distinct()
        if (ids.isEmpty()) return Result.Ok(null)
        val gate = Semaphore(5)
        val outcomes = coroutineScope {
            ids.map { nu -> async { gate.withPermit { players.ratings(nu, day) } } }.awaitAll()
        }
        if (outcomes.any { it == RatingOutcome.NeedsLogin }) return Result.NeedsLogin
        val values = outcomes.filterIsInstance<RatingOutcome.Values>().mapNotNull { v ->
            when {
                v.atDate != null -> v.atDate to true
                v.ttr != null -> v.ttr to true
                v.qttr != null -> v.qttr to false
                else -> null
            }
        }
        return Result.Ok(average(values)?.copy(lineup = true))
    }

    /** Mittelwert aller Werte; [values] sind Paare aus Wert und Kennzeichen "TTR". Leer → null. */
    private fun average(values: List<Pair<Int, Boolean>>): TeamAverage? {
        if (values.isEmpty()) return null
        val avg = Spieltag.topAverage(values.map { it.first }, n = values.size) ?: return null
        return TeamAverage(avg, values.size, values.all { it.second })
    }
}
