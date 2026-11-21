package app.punktgenau.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackBar(onClose)
        HeroCard {
            Text("MANNSCHAFT", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            Text(team.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(
                listOfNotNull(team.group.name, details?.clubName?.takeIf { it != team.name }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChoice("Liga", { links.league(team.group) }, Modifier.weight(1f))
                HeroChoice("Verein", clubNr?.let { nr -> { links.club(nr) } }, Modifier.weight(1f))
            }
        }
        Segmented(listOf("Rangliste", "Spielplan", "Bilanzen"), state.tab) { state.tab = it }
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
                    val day = MatchTime.day(a.date)
                    AppCard(onClick = report?.let { r -> { links.report(r) } }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (day != null) DateBadge("%02d.%02d.".format(day.dayOfMonth, day.monthValue), day.year.toString())
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    listOfNotNull(
                                        MatchTime.time(a.date)?.let { "$it Uhr" },
                                        when (a.home) { true -> "Heim"; false -> "Auswärts"; null -> null },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(a.opponentName, style = MaterialTheme.typography.titleMedium)
                                if (running) LivePill(if (a.live) "LIVE" else "läuft")
                            }
                            a.result?.let { ScoreBox(it) }
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
                ListCard(
                    title = p.name,
                    subtitle = "Einsätze ${p.appearances}" + if (expanded) "" else " · antippen für Details",
                    onClick = { if (expanded) open.remove(p.playerId) else open.add(p.playerId) },
                    leading = { PositionBox(p.position) },
                    trailing = { TrailingNumber("${p.won}:${p.lost}", "Gesamt", MaterialTheme.colorScheme.primary) },
                    extra = if (expanded) ({
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        p.perOpponent.forEach { o ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("Gegen Position ${o.opponentRank}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${o.won}:${o.lost}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (p.playerId.startsWith("NU")) {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                                AccentAction("Spieler ansehen", { links.player(PlayerLink(p.playerId, p.name, clubNr = team.clubNr, team = team)) })
                            }
                        }
                    }) else null,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Aufstellungsposition (z. B. "1.2") als kleines Feld links. */
@Composable
private fun PositionBox(text: String) {
    Box(
        Modifier.widthIn(min = 44.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}
