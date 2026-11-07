package app.punktgenau.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.punktgenau.data.HistoryEvent
import app.punktgenau.data.ProfileParser
import app.punktgenau.data.Season
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.Result
import app.punktgenau.data.TeamInfo
import app.punktgenau.data.TeamMember
import app.punktgenau.data.TeamRepository
import androidx.compose.material3.FilterChip
import java.time.LocalDate
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Teams : Step
    data class Dates(val team: TeamInfo) : Step
    data class Lineup(val appointment: Appointment) : Step
}

/** Termin wählen und Gegner aus der Meldeliste in den Rechner übernehmen (). */
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
    var round by remember { mutableStateOf(0) } // 0 = Hinrunde, 1 = Rückrunde
    var meetings by remember { mutableStateOf<List<HistoryEvent>>(emptyList()) }
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

    fun openDates(t0: TeamInfo) {
        team = t0; step = Step.Dates(t0); busy = true; message = null
        if (ownNuId != null) scope.launch { meetings = players.events(ownNuId) }
        scope.launch { handle(teams.schedule(t0.id, t0.season)) { dates = it; if (it.isEmpty()) message = "Keine Termine gefunden." } }
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

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Row { TextButton({
            when (val s = step) {
                Step.Teams -> onClose()
                is Step.Dates -> if (teamList.size > 1) { step = Step.Teams; message = null } else onClose()
                is Step.Lineup -> { step = Step.Dates(team ?: teamList.first()); message = null }
            }
        }) { Text(if (step == Step.Teams) "Schließen" else "Zurück") } }
        Text(
            when (step) {
                Step.Teams -> "Mannschaft wählen"
                is Step.Dates -> "Termin wählen"
                is Step.Lineup -> "Gegner antippen, um sie zum Rechner hinzuzufügen"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        if (step is Step.Dates) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(round == 0, { round = 0 }, { Text("Hinrunde") })
                FilterChip(round == 1, { round = 1 }, { Text("Rückrunde") })
            }
            if (round == 1) Text(
                "Die Website liefert pro Gegner nur einen Termin. Rückspiele erscheinen mit Datum, sobald sie in deinem Verlauf stehen, sonst mit offenem Termin und aktuellen Werten.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (step is Step.Lineup) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(useDate, { useDate = true }, { Text("Wert zum Termin") })
                FilterChip(!useDate, { useDate = false }, { Text("Aktueller Wert") })
            }
            Text(
                if (useDate) "Die Werte gelten für den Stand vor dem Spieltag. So rechnet der Rechner so, wie das Spiel gewertet wurde."
                else "Die Werte sind die heutigen. Sie weichen vom Stand zum Spieltag ab.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it) }
        LazyColumn {
            when (step) {
                Step.Teams -> items(teamList) { t -> ListEntry(t.name, t.league) { openDates(t) } }
                is Step.Dates -> {
                    val seasonStart = Season.start(LocalDate.now())
                    val shown = if (round == 0) dates else dates.map { a ->
                        val ev = ProfileParser.returnLegOf(meetings, a.opponentName, dayOf(a), seasonStart)
                        a.copy(date = ev?.dateTime)
                    }.sortedWith(compareBy(nullsLast()) { it.date })
                    items(shown) { a -> ListEntry(a.opponentName, formatDate(a.date) ?: "Termin offen") { openLineup(a) } }
                }
                is Step.Lineup -> items(lineup) { m ->
                    val done = m.nuId != null && m.nuId in added
                    ListEntry((m.rank?.let { "$it. " } ?: "") + m.displayName, if (done) "hinzugefügt" else null) { addPlayer(m) }
                }
            }
        }
    }
}

private fun dayOf(a: Appointment?): LocalDate? =
    a?.date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun formatDate(iso: String?): String? {
    val d = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return iso
    val time = iso.drop(11).take(5)
    return "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year) + if (time.length == 5) ", $time Uhr" else ""
}

@Composable
private fun ListEntry(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
