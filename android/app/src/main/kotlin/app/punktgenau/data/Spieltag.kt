package app.punktgenau.data

import app.punktgenau.data.Result
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Anstoßzeiten des Spielplans. Die Website liefert sie in Weltzeit ("…T19:00:00.000+00:00" für 20:00 Uhr
 * deutscher Zeit); angezeigt und verglichen wird in deutscher Ortszeit.
 */
object MatchTime {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** Ortszeit des Anstoßes; null, wenn das Datum fehlt oder nicht lesbar ist. */
    fun local(iso: String?): LocalDateTime? {
        if (iso == null) return null
        return runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso.take(19)) }.getOrNull()
    }

    fun day(iso: String?): LocalDate? =
        local(iso)?.toLocalDate() ?: iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Uhrzeit als "HH:mm"; null ohne lesbare Zeit. */
    fun time(iso: String?): String? = local(iso)?.let { "%02d:%02d".format(it.hour, it.minute) }

    fun now(): LocalDateTime = LocalDateTime.now(zone)
}

/** Ein Spiel einer eigenen Mannschaft mit dem Termin aus deren Spielplan. */
data class SpieltagMatch(val team: TeamInfo, val appointment: Appointment) {
    val day: LocalDate? get() = MatchTime.day(appointment.date)
    val time: String? get() = MatchTime.time(appointment.date)
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

    /**
     * Läuft das Spiel gerade? Ja, wenn die Website es als live meldet. Sonst zählt die Uhr: ab dem Anstoß
     * bis [RUNNING_HOURS] Stunden danach, solange der Spielbericht nicht abgeschlossen ist. Viele Vereine
     * erfassen nicht live; dann bleibt `live` während des ganzen Spiels false und das Ergebnis 0:0.
     */
    fun isRunning(a: Appointment, now: LocalDateTime): Boolean {
        if (a.live) return true
        if (a.complete || a.state == "done") return false
        val start = MatchTime.local(a.date) ?: return false
        return !now.isBefore(start) && now.isBefore(start.plusHours(RUNNING_HOURS))
    }

    /** Die eigene Mannschaft, wie sie in der Spieltag-Ansicht und im Kalender benannt wird. */
    fun ownRef(team: TeamInfo, a: Appointment): TeamRef = TeamRef(
        GroupRef(
            team.organization ?: "RTTVR",
            team.season ?: Season.label(LocalDate.now()),
            a.leagueId ?: team.groupId.orEmpty(),
            team.league ?: team.name,
        ),
        team.id,
        team.club?.let { "$it – ${team.name}" } ?: team.name,
        a.ownClubNr,
    )

    /**
     * Verweis auf den Spielbericht eines Termins aus dem Spielplan von [own], mit beiden Mannschaften.
     * Null, wenn der Spielplan keine Spielkennung nennt. Ein Ergebnis ist dafür nicht nötig: die Kennung
     * steht schon vor dem Spiel fest.
     */
    fun reportLink(own: TeamRef, a: Appointment, now: LocalDateTime): ReportLink? {
        val meeting = a.meetingId ?: return null
        val group = a.leagueId?.takeIf { it != own.group.groupId }?.let { own.group.copy(groupId = it) } ?: own.group
        val ownRef = own.copy(group = group, name = a.ownName ?: own.name, clubNr = own.clubNr ?: a.ownClubNr)
        val opponent = TeamRef(group, a.opponentTeamId, a.opponentName, a.opponentClubNr)
        val home = if (a.home == false) opponent else ownRef
        val away = if (a.home == false) ownRef else opponent
        return ReportLink(
            url = reportUrl(group.association, group.season, group.groupId, meeting),
            title = "${home.name} gegen ${away.name}",
            running = isRunning(a, now),
            home = home,
            away = away,
        )
    }

    /** Heim und Gast aus dem Namen eines Verlaufs-Ereignisses ("Liga | Heim : Gast"); null bei anderem Aufbau. */
    fun eventTeams(eventName: String): Pair<String, String>? {
        val pairing = eventName.split(" | ", limit = 2).getOrNull(1) ?: return null
        val sides = pairing.split(" : ", limit = 2)
        return if (sides.size == 2) sides[0].trim() to sides[1].trim() else null
    }

    private fun norm(s: String) = s.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * Das Spiel aus einem Spielplan, das zu einem Verlaufs-Ereignis gehört: gleicher Tag und gleiche Paarung.
     * Der Verlauf nennt keine Spielkennung, nur Datum und Mannschaftsnamen. Nennt der Spielplan den eigenen
     * Namen nicht, genügt der Gegner auf einer der beiden Seiten.
     */
    fun matchEvent(day: LocalDate, home: String, away: String, schedule: List<Appointment>): Appointment? {
        val sameDay = schedule.filter { MatchTime.day(it.date) == day }
        val h = norm(home)
        val w = norm(away)
        return sameDay.firstOrNull { a ->
            val own = a.ownName?.let { norm(it) }
            val opp = norm(a.opponentName)
            if (a.home == false) opp == h && own == w else own == h && opp == w
        } ?: sameDay.firstOrNull { a -> a.ownName == null && norm(a.opponentName).let { it == h || it == w } }
    }

    /** So lange nach dem Anstoß gilt ein nicht abgeschlossenes Spiel höchstens als laufend. */
    const val RUNNING_HOURS = 4L

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
    suspend fun refreshLive(current: List<SpieltagMatch>, now: LocalDateTime = MatchTime.now()): Result<List<SpieltagMatch>> {
        val liveTeams = current.filter { Spieltag.isRunning(it.appointment, now) }.map { it.team.id }.toSet()
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

/**
 * Findet zu einem Ereignis des TTR-Verlaufs den Spielbericht. Der Verlauf kennt das Spiel nur über Datum und
 * Mannschaftsnamen; die Spielkennung steht im Spielplan der Mannschaft. Verknüpfbar sind deshalb nur Spiele
 * der laufenden Saison (ältere Spielpläne werden nicht geladen).
 */
class MatchFinder(private val teams: TeamRepository, private val today: () -> LocalDate = { LocalDate.now() }) {

    /** [candidates]: Mannschaften, in denen die Person gespielt haben kann; ihre Spielpläne werden der Reihe nach geprüft. */
    suspend fun find(event: HistoryEvent, candidates: List<TeamRef>): ReportLink? {
        val (home, away) = Spieltag.eventTeams(event.name) ?: return null
        val now = today()
        val seasonStart = LocalDate.of(if (now.monthValue >= 7) now.year else now.year - 1, 7, 1)
        if (event.date < seasonStart) return null
        for (t in candidates) {
            val info = TeamInfo(t.teamId, t.name, t.group.name, t.group.season, t.group.groupId, t.group.association)
            val schedule = (teams.schedule(info, ScheduleFilter.ALL) as? Result.Ok)?.value ?: continue
            val a = Spieltag.matchEvent(event.date, home, away, schedule) ?: continue
            return Spieltag.reportLink(t, a, MatchTime.now())
        }
        return null
    }
}

/** Abstand der Aktualisierung laufender Spiele in Millisekunden (nur solange der Spieltag offen ist). */
const val LIVE_INTERVAL_MS = 45_000L
