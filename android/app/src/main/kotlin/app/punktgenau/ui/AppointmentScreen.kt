package app.punktgenau.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Appointment
import app.punktgenau.data.ScheduleFilter
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.Result
import app.punktgenau.data.TeamInfo
import app.punktgenau.data.TeamMember
import app.punktgenau.data.TeamRepository
import java.time.LocalDate
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Teams : Step
    data class Dates(val team: TeamInfo) : Step
    data class Lineup(val appointment: Appointment) : Step
}

/** Termin wählen und Gegner aus der Meldeliste in den Rechner übernehmen. */
@Composable
fun AppointmentScreen(
    teams: TeamRepository,
    players: PlayerRepository,
    ownNuId: String?,
    onAddOpponent: (Int) -> Unit,
    onSetOwn: (Int) -> Unit,
    onClose: () -> Unit,
    onNeedsLogin: () -> Unit,
) {
    var step by remember { mutableStateOf<Step>(Step.Teams) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var teamList by remember { mutableStateOf<List<TeamInfo>>(emptyList()) }
    var dates by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var lineup by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var team by remember { mutableStateOf<TeamInfo?>(null) }
    var useDate by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(ScheduleFilter.ALL) }
    val added = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun <T> handle(r: Result<T>, ok: (T) -> Unit) {
        busy = false
        when (r) {
            is Result.Ok -> ok(r.value)
            Result.NeedsLogin -> onNeedsLogin()
            is Result.Failed -> message = r.message
        }
    }

    fun loadDates(t0: TeamInfo, f: ScheduleFilter) {
        filter = f; busy = true; message = null; dates = emptyList()
        scope.launch { handle(teams.schedule(t0, f)) { dates = it; if (it.isEmpty()) message = "Keine Termine gefunden." } }
    }

    fun openDates(t0: TeamInfo) {
        team = t0; step = Step.Dates(t0); busy = true; message = null
        loadDates(t0, filter)
    }

    fun openLineup(a: Appointment) {
        step = Step.Lineup(a); busy = true; message = null; added.clear()
        if (useDate && ownNuId != null && dayOf(a) != null) scope.launch {
            val own = players.ratings(ownNuId, dayOf(a))
            if (own is RatingOutcome.Values) (own.atDate)?.let(onSetOwn)
        }
        scope.launch { handle(teams.players(a.opponentTeamId)) { lineup = it; if (it.isEmpty()) message = "Keine Meldeliste gefunden." } }
    }

    fun lineupAppointment(): Appointment? = (step as? Step.Lineup)?.appointment

    fun addPlayer(m: TeamMember) {
        val id = m.nuId ?: run { message = "Für ${m.displayName} fehlt die ID."; return }
        scope.launch {
            message = null
            when (val r = players.ratings(id, if (useDate) dayOf(lineupAppointment()) else null)) {
                RatingOutcome.NeedsLogin -> onNeedsLogin()
                is RatingOutcome.Failed -> message = r.message
                is RatingOutcome.Values -> {
                    val v = (if (useDate) r.atDate else null) ?: r.ttr ?: r.qttr
                    if (useDate && dayOf(lineupAppointment()) != null && r.atDate == null) message = "Kein Verlauf zum Termin gefunden, aktueller Wert verwendet."
                    if (v == null) message = "Kein Wert für ${m.displayName} erhalten."
                    else { onAddOpponent(v); added.add(id) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        handle(teams.myTeams()) {
            teamList = it
            when {
                it.isEmpty() -> message = "Keine Mannschaften gefunden."
                it.size == 1 -> openDates(it[0])
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackBar(
            {
                when (step) {
                    Step.Teams -> onClose()
                    is Step.Dates -> if (teamList.size > 1) { step = Step.Teams; message = null } else onClose()
                    is Step.Lineup -> { step = Step.Dates(team ?: teamList.first()); message = null }
                }
            },
            label = if (step == Step.Teams) "Schließen" else "Zurück",
        ) {
            if (step is Step.Lineup || added.isNotEmpty()) AccentAction(if (added.isEmpty()) "Zum Rechner" else "Fertig, zum Rechner (${added.size})", onClose)
        }
        ScreenTitle(
            when (step) {
                Step.Teams -> "Mannschaft wählen"
                is Step.Dates -> "Termin wählen"
                is Step.Lineup -> "Gegner wählen"
            },
        )
        // Schrittanzeige: wo im Ablauf Mannschaft, Termin, Gegner man gerade steht.
        val current = when (step) { Step.Teams -> 0; is Step.Dates -> 1; is Step.Lineup -> 2 }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1 Mannschaft", "2 Termin", "3 Gegner").forEachIndexed { i, s ->
                if (i == current) Pill(s, color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
                else Pill(s)
            }
        }
        if (step is Step.Dates) {
            val filters = listOf(ScheduleFilter.ALL, ScheduleFilter.FIRST_HALF, ScheduleFilter.SECOND_HALF)
            Segmented(listOf("Gesamt", "Vorrunde", "Rückrunde"), filters.indexOf(filter)) { i -> team?.let { loadDates(it, filters[i]) } }
        }
        if (step is Step.Lineup) {
            InfoText("Gegner antippen, um sie zum Rechner hinzuzufügen.")
            Segmented(listOf("Wert zum Termin", "Aktueller Wert"), if (useDate) 0 else 1) { useDate = it == 0 }
            Text(
                if (useDate) "Die Werte gelten für den Stand vor dem Spieltag. So rechnet der Rechner so, wie das Spiel gewertet wurde."
                else "Die Werte sind die heutigen. Sie weichen vom Stand zum Spieltag ab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) LoadingBox()
        message?.let { InfoText(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (step) {
                Step.Teams -> items(teamList) { t -> ListCard(t.name, t.league, onClick = { openDates(t) }, leading = { LetterBadge(t.name) }) }
                is Step.Dates -> items(dates) { a ->
                    val where = when (a.home) { true -> "Heim"; false -> "Auswärts"; null -> null }
                    val day = app.punktgenau.data.MatchTime.day(a.date)
                    val sub = listOfNotNull(
                        if (day == null) formatDate(a.date) else app.punktgenau.data.MatchTime.time(a.date)?.let { "$it Uhr" },
                        where,
                    ).joinToString(" · ")
                    ListCard(
                        a.opponentName,
                        sub.ifBlank { null },
                        onClick = { openLineup(a) },
                        leading = day?.let { d -> { DateBadge("%02d.%02d.".format(d.dayOfMonth, d.monthValue), d.year.toString()) } },
                        trailing = { a.result?.let { ScoreBox(it, strong = false) } ?: Chevron() },
                    )
                }
                is Step.Lineup -> items(lineup) { m ->
                    val done = m.nuId != null && m.nuId in added
                    ListCard(
                        m.displayName,
                        if (done) "hinzugefügt" else null,
                        onClick = { addPlayer(m) },
                        leading = { m.rank?.let { RankBadge(it, highlight = done) } ?: LetterBadge(m.displayName) },
                        trailing = {
                            if (done) Pill("✓ im Rechner", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            else Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}

private fun dayOf(a: Appointment?): LocalDate? =
    a?.date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun formatDate(iso: String?): String? {
    val d = app.punktgenau.data.MatchTime.day(iso) ?: return iso
    return "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year) +
        (app.punktgenau.data.MatchTime.time(iso)?.let { ", $it Uhr" }.orEmpty())
}
