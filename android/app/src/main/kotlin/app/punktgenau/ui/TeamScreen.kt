package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Appointment
import app.punktgenau.data.ClickTtRepository
import app.punktgenau.data.MatchTime
import app.punktgenau.data.PlayerLine
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RankEntry
import app.punktgenau.data.Result
import app.punktgenau.data.ScheduleFilter
import app.punktgenau.data.Spieltag
import app.punktgenau.data.TeamDetails
import app.punktgenau.data.TeamInfo
import app.punktgenau.data.TeamRef
import app.punktgenau.data.TeamRepository

/** Zustand der Mannschaftsansicht, der das Öffnen eines weiteren Bildschirms überdauert. */
class TeamViewState {
    /** 0 = Rangliste, 1 = Spielplan, 2 = Bilanzen */
    var tab by mutableIntStateOf(0)
}

/**
 * Eine Mannschaft für sich: ihre Rangliste nach TTR oder QTTR (ohne die ganze Liga zu laden), ihr Spielplan
 * und die Bilanzen. Von hier geht es zur Liga, zum Verein, zu den Spielern und zu den Spielberichten.
 */
@Composable
fun TeamScreen(
    clickTt: ClickTtRepository,
    teams: TeamRepository,
    players: PlayerRepository,
    team: TeamRef,
    state: TeamViewState,
    links: Links,
    onClose: () -> Unit,
) {
    var details by remember { mutableStateOf<TeamDetails?>(null) }
    LaunchedEffect(team) { details = (clickTt.teamDetails(team.group, team.teamId) as? Result.Ok)?.value }
    BackHandler(onBack = onClose)
    val clubNr = team.clubNr ?: details?.clubNr
    val ref = team.copy(clubNr = clubNr)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle(team.name)
        InfoText(listOfNotNull(team.group.name, details?.clubName?.takeIf { it != team.name }).joinToString(" · "))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ links.league(team.group) }, Modifier.weight(1f)) { Text("Liga") }
            OutlinedButton({ clubNr?.let(links.club) }, Modifier.weight(1f), enabled = clubNr != null) { Text("Verein") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.tab == 0, { state.tab = 0 }, { Text("Rangliste") })
            FilterChip(state.tab == 1, { state.tab = 1 }, { Text("Spielplan") })
            FilterChip(state.tab == 2, { state.tab = 2 }, { Text("Bilanzen") })
        }
        when (state.tab) {
            0 -> TeamRanking(teams, players, ref, links)
            1 -> TeamSchedule(teams, ref, links)
            else -> TeamBalances(clickTt, ref, links)
        }
    }
}

@Composable
private fun TeamRanking(teams: TeamRepository, players: PlayerRepository, team: TeamRef, links: Links) {
    var progress by remember { mutableStateOf("Lade Aufstellung …") }
    var failed by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<RankEntry>() }
    LaunchedEffect(team.teamId) {
        entries.clear()
        failed = null
        val members = when (val r = teams.players(team.teamId)) {
            is Result.Ok -> r.value
            Result.NeedsLogin -> { failed = "Bitte zuerst anmelden."; return@LaunchedEffect }
            is Result.Failed -> { failed = r.message; return@LaunchedEffect }
        }
        val seeds = members.mapNotNull { m -> m.nuId?.let { RankEntry(it, m.displayName, team.name, null, null, team.clubNr, team) } }
        if (seeds.isEmpty()) { failed = "Keine Spieler in der Aufstellung gefunden."; return@LaunchedEffect }
        loadRatings(players, seeds, onEntry = { entries.add(it) }) { done, total -> progress = "Lade Werte: $done von $total" }
        progress = ""
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { RatingModeChips() }
        failed?.let { item { InfoText(it) } }
        if (progress.isNotEmpty() && failed == null) item { InfoText(progress) }
        // Die Vereinsnummer kann nach dem Laden der Rangliste eintreffen; die Verweise nehmen den aktuellen Stand.
        rankingItems(entries.map { it.copy(clubNr = team.clubNr, teamRef = team) }, RatingModeState.mode, links.player)
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TeamSchedule(teams: TeamRepository, team: TeamRef, links: Links) {
    var state by remember { mutableStateOf<Result<List<Appointment>>?>(null) }
    LaunchedEffect(team.teamId) {
        val info = TeamInfo(team.teamId, team.name, team.group.name, team.group.season, team.group.groupId, team.group.association)
        state = teams.schedule(info, ScheduleFilter.ALL)
    }
    val now = MatchTime.now()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = state) {
            null -> item { LoadingBox() }
            Result.NeedsLogin -> item { InfoText("Bitte zuerst anmelden.") }
            is Result.Failed -> item { InfoText(s.message) }
            is Result.Ok -> {
                if (s.value.isEmpty()) item { InfoText("Keine Spiele gefunden.") }
                items(s.value) { a ->
                    val report = Spieltag.reportLink(team, a, now)
                    val running = Spieltag.isRunning(a, now)
                    AppCard(onClick = report?.let { r -> { links.report(r) } }) {
                        Text(
                            listOfNotNull(
                                MatchTime.day(a.date)?.let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) },
                                MatchTime.time(a.date)?.let { "$it Uhr" },
                                when (a.home) { true -> "Heim"; false -> "Auswärts"; null -> null },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(a.opponentName, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            a.result?.let { Pill(it) }
                            if (running) Pill(if (a.live) "LIVE" else "läuft", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TeamBalances(clickTt: ClickTtRepository, team: TeamRef, links: Links) {
    var state by remember { mutableStateOf<Result<List<PlayerLine>>?>(null) }
    val open = remember { mutableStateListOf<String>() }
    LaunchedEffect(team.teamId) { state = clickTt.teamPlayers(team.group, team.teamId) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = state) {
            null -> item { LoadingBox() }
            Result.NeedsLogin -> item { InfoText("Bitte zuerst anmelden.") }
            is Result.Failed -> item { InfoText(s.message) }
            is Result.Ok -> items(s.value) { p ->
                val expanded = p.playerId in open
                AppCard(onClick = { if (expanded) open.remove(p.playerId) else open.add(p.playerId) }) {
                    Text("${p.position}  ${p.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Einsätze ${p.appearances} · Gesamt ${p.won}:${p.lost}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (expanded) {
                        p.perOpponent.forEach { o -> Text("Gegen Position ${o.opponentRank}: ${o.won}:${o.lost}", style = MaterialTheme.typography.bodyMedium) }
                        if (p.playerId.startsWith("NU")) {
                            TextButton({ links.player(PlayerLink(p.playerId, p.name, clubNr = team.clubNr, team = team)) }) {
                                Text("Spieler ansehen", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
