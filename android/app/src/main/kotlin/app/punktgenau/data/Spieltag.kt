package app.punktgenau.data

import app.punktgenau.data.Result
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
data class TeamAverage(val value: Int, val count: Int, val isTtr: Boolean)

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
class SpieltagRepository(private val teams: TeamRepository, private val players: PlayerRepository) {

    /** Alle Spiele der eigenen Mannschaften der laufenden Saison. */
    suspend fun ownMatches(): Result<List<SpieltagMatch>> {
        val mine = when (val r = teams.myTeams()) {
            is Result.Ok -> r.value
            Result.NeedsLogin -> return Result.NeedsLogin
            is Result.Failed -> return r
        }
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
        val top = values.sortedByDescending { it.first }.take(4)
        if (top.isEmpty()) return Result.Ok(null)
        val avg = Spieltag.topAverage(top.map { it.first }) ?: return Result.Ok(null)
        return Result.Ok(TeamAverage(avg, top.size, top.all { it.second }))
    }
}
