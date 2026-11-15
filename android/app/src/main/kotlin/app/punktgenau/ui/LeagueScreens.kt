package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.BalanceRow
import app.punktgenau.data.ClickTtRepository
import app.punktgenau.data.District
import app.punktgenau.data.GroupMeeting
import app.punktgenau.data.GroupRef
import app.punktgenau.data.LeagueGroup
import app.punktgenau.data.LeagueTable
import app.punktgenau.data.MatchTime
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RankEntry
import app.punktgenau.data.ReportLink
import app.punktgenau.data.Result
import app.punktgenau.data.Season
import app.punktgenau.data.Spieltag
import app.punktgenau.data.TeamInfo
import app.punktgenau.data.TeamRef
import app.punktgenau.data.TeamRepository
import java.time.LocalDate

internal sealed interface Nav {
    data object Home : Nav
    data object Districts : Nav
    data class Leagues(val district: District) : Nav
    data class Hub(val group: GroupRef) : Nav
    data class Table(val group: GroupRef) : Nav
    data class Schedule(val group: GroupRef) : Nav
    data class Ranking(val group: GroupRef) : Nav
    data class PickTeam(val group: GroupRef) : Nav
    data class TtrRanking(val group: GroupRef) : Nav
}

/** Seite innerhalb des Liga-Bereichs; überdauert das Öffnen einer Mannschaft, Person oder eines Spielberichts. */
class LeagueNavState(val initial: GroupRef? = null) {
    internal var nav by mutableStateOf<Nav>(if (initial != null) Nav.Hub(initial) else Nav.Home)
}

/** Liga-Reiter: eigene Ligen, Ligenauswahl, Seiten der Gruppe. */
@Composable
fun LeagueScreen(
    clickTt: ClickTtRepository,
    teams: TeamRepository,
    players: PlayerRepository,
    loggedIn: Boolean,
    state: LeagueNavState,
    links: Links,
    onBack: (() -> Unit)? = null,
) {
    val initial = state.initial
    var nav by state::nav
    var myTeams by remember { mutableStateOf<List<TeamInfo>>(emptyList()) }
    LaunchedEffect(loggedIn) {
        if (loggedIn) (teams.myTeams() as? Result.Ok)?.let { myTeams = it.value }
    }
    val association = myTeams.firstOrNull()?.organization ?: "RTTVR"
    val season = myTeams.firstOrNull()?.season ?: Season.label(LocalDate.now())
    val ownTeamIds = myTeams.map { it.id }.toSet()

    fun back(n: Nav): Nav = when (n) {
        Nav.Home -> Nav.Home
        Nav.Districts -> Nav.Home
        is Nav.Leagues -> Nav.Districts
        is Nav.Hub -> Nav.Home
        is Nav.Table -> Nav.Hub(n.group)
        is Nav.Schedule -> Nav.Hub(n.group)
        is Nav.Ranking -> Nav.Hub(n.group)
        is Nav.PickTeam -> Nav.Hub(n.group)
        is Nav.TtrRanking -> Nav.Hub(n.group)
    }
    BackHandler(enabled = nav != Nav.Home || onBack != null) { if (nav == Nav.Home || (initial != null && nav == Nav.Hub(initial))) onBack?.invoke() else nav = back(nav) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        if (nav != Nav.Home || onBack != null) {
            TextButton({ if (nav == Nav.Home || (initial != null && nav == Nav.Hub(initial))) onBack?.invoke() else nav = back(nav) }) { Text("Zurück") }
        }
        when (val n = nav) {
            Nav.Home -> LeagueHome(myTeams, loggedIn, onOpen = { nav = Nav.Hub(it) }, onOpenTeam = links.team, onChoose = { nav = Nav.Districts })
            Nav.Districts -> DistrictPage(clickTt, association, season) { nav = Nav.Leagues(it) }
            is Nav.Leagues -> LeaguePickPage(clickTt, association, season, n.district) { g ->
                nav = Nav.Hub(GroupRef(association, season, g.groupId, g.name))
            }
            is Nav.Hub -> HubPage(n.group,
                onTable = { nav = Nav.Table(n.group) }, onSchedule = { nav = Nav.Schedule(n.group) },
                onRanking = { nav = Nav.Ranking(n.group) }, onTeams = { nav = Nav.PickTeam(n.group) },
                onTtr = { nav = Nav.TtrRanking(n.group) })
            is Nav.Table -> TablePage(clickTt, n.group, ownTeamIds, links)
            is Nav.Schedule -> SchedulePage(clickTt, n.group, ownTeamIds, links)
            is Nav.Ranking -> RankingPage(clickTt, n.group, links)
            is Nav.PickTeam -> PickTeamPage(clickTt, n.group) { id, name -> links.team(TeamRef(n.group, id, name)) }
            is Nav.TtrRanking -> TtrRankingPage(clickTt, teams, players, n.group, links)
        }
    }
}

@Composable
private fun <T> Loaded(state: Result<T>?, content: @Composable (T) -> Unit) {
    when (state) {
        null -> LoadingBox()
        is Result.Ok -> content(state.value)
        Result.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
        is Result.Failed -> InfoText(state.message)
    }
}

@Composable
private fun LeagueHome(myTeams: List<TeamInfo>, loggedIn: Boolean, onOpen: (GroupRef) -> Unit, onOpenTeam: (TeamRef) -> Unit, onChoose: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Liga") }
        item { SectionLabel("Meine Ligen") }
        if (myTeams.isEmpty()) item { InfoText(if (loggedIn) "Lade deine Mannschaften …" else "Anmelden, um deine Ligen zu sehen.") }
        items(myTeams) { t ->
            AppCard(onClick = {
                val org = t.organization; val gid = t.groupId; val s = t.season
                if (org != null && gid != null && s != null) onOpen(GroupRef(org, s, gid, t.league ?: t.name))
            }) {
                Text(t.league ?: t.name, style = MaterialTheme.typography.titleMedium)
                Text(t.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val org = t.organization; val gid = t.groupId; val s = t.season
                if (org != null && gid != null && s != null) {
                    TextButton({ onOpenTeam(TeamRef(GroupRef(org, s, gid, t.league ?: t.name), t.id, t.name)) }) { Text("Mannschaft ansehen") }
                }
            }
        }
        item { SectionLabel("Andere Liga") }
        item { AppCard(onClick = onChoose) { Text("Liga auswählen", style = MaterialTheme.typography.titleMedium) } }
    }
}

@Composable
private fun DistrictPage(clickTt: ClickTtRepository, association: String, season: String, onPick: (District) -> Unit) {
    var state by remember { mutableStateOf<Result<List<District>>?>(null) }
    LaunchedEffect(association, season) { state = clickTt.districts(association, season) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Bezirk wählen") }
        item { Loaded(state) { } }
        val list = (state as? Result.Ok)?.value.orEmpty()
        items(list) { d -> AppCard(onClick = { onPick(d) }) { Text(d.name, style = MaterialTheme.typography.titleMedium) } }
    }
}

@Composable
private fun LeaguePickPage(clickTt: ClickTtRepository, association: String, season: String, district: District, onPick: (LeagueGroup) -> Unit) {
    var state by remember { mutableStateOf<Result<List<LeagueGroup>>?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(district) { state = clickTt.groups(association, season, district) }
    val groups = (state as? Result.Ok)?.value.orEmpty()
    val categories = groups.map { it.category }.distinct()
    val shown = groups.filter { it.category == (category ?: categories.firstOrNull()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle(district.name) }
        item { Loaded(state) { } }
        if (categories.size > 1) item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { c -> FilterChip((category ?: categories.first()) == c, { category = c }, { Text(c) }) }
            }
        }
        items(shown) { g -> AppCard(onClick = { onPick(g) }) { Text(g.name, style = MaterialTheme.typography.titleMedium) } }
    }
}

@Composable
private fun HubPage(g: GroupRef, onTable: () -> Unit, onSchedule: () -> Unit, onRanking: () -> Unit, onTeams: () -> Unit, onTtr: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle(g.name) }
        item { InfoText("Saison ${g.season}") }
        item { HubTile("Tabelle", "Platzierung, Spiele, Punkte", onTable) }
        item { HubTile("Spielplan", "Alle Spiele der Gruppe, Vor- und Rückrunde", onSchedule) }
        item { HubTile("Bilanz-Rangliste", "Beste Spieler nach Bilanz", onRanking) }
        item { HubTile("Mannschaften", "Rangliste, Spielplan und Bilanzen einer Mannschaft", onTeams) }
        item { HubTile("TTR-Rangliste", "Alle Spieler der Gruppe nach TTR oder QTTR (lädt einige Sekunden)", onTtr) }
    }
}

@Composable
private fun HubTile(title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RankBadge(rank: Int, highlight: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(36.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                rank.toString(), fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TablePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>, links: Links) {
    var state by remember { mutableStateOf<Result<LeagueTable>?>(null) }
    LaunchedEffect(g) { state = clickTt.table(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Tabelle") }
        item { InfoText(g.name) }
        item { Loaded(state) { t -> t.ttrAverage?.let { InfoText("Ø TTR der Liga: $it") } } }
        val table = (state as? Result.Ok)?.value
        items(table?.rows.orEmpty()) { r ->
            val mine = r.teamId in own
            AppCard(onClick = { links.team(TeamRef(g, r.teamId, r.teamName)) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RankBadge(r.rank, mine)
                    Column(Modifier.weight(1f)) {
                        Text(r.teamName, style = MaterialTheme.typography.titleMedium)
                        Text("${r.meetings} Spiele · ${r.won} S ${r.tie} U ${r.lost} N · Spiele ${r.matchesWon}:${r.matchesLost}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${r.pointsWon}:${r.pointsLost}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SchedulePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>, links: Links) {
    var filter by remember { mutableStateOf("gesamt") }
    var state by remember { mutableStateOf<Result<List<GroupMeeting>>?>(null) }
    LaunchedEffect(g, filter) { state = null; state = clickTt.schedule(g, filter) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Spielplan") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == "gesamt", { filter = "gesamt" }, { Text("Gesamt") })
                FilterChip(filter == "vr", { filter = "vr" }, { Text("Vorrunde") })
                FilterChip(filter == "rr", { filter = "rr" }, { Text("Rückrunde") })
            }
        }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value.orEmpty()) { m ->
            val mine = m.homeId in own || m.awayId in own
            val running = meetingRunning(m)
            val report = m.meetingId?.let { id ->
                ReportLink(
                    url = Spieltag.reportUrl(g.association, g.season, g.groupId, id),
                    title = "${m.home} gegen ${m.away}",
                    running = running,
                    home = m.homeId?.let { TeamRef(g, it, m.home, m.homeClubNr) },
                    away = m.awayId?.let { TeamRef(g, it, m.away, m.awayClubNr) },
                )
            }
            AppCard(onClick = report?.let { r -> { links.report(r) } }) {
                Text(formatShort(m.date), style = MaterialTheme.typography.bodySmall, color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${m.home} – ${m.away}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    m.result?.let { Pill(it) }
                    if (running) Pill(if (m.live) "LIVE" else "läuft", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun formatShort(iso: String?): String {
    val d = MatchTime.day(iso) ?: return iso.orEmpty()
    return "%02d.%02d.%02d".format(d.dayOfMonth, d.monthValue, d.year % 100) + (MatchTime.time(iso)?.let { " $it" }.orEmpty())
}

/** Wie [Spieltag.isRunning], für ein Spiel aus dem Spielplan der Gruppe. */
private fun meetingRunning(m: GroupMeeting): Boolean {
    if (m.live) return true
    if (m.complete || m.state == "done") return false
    val start = MatchTime.local(m.date) ?: return false
    val now = MatchTime.now()
    return !now.isBefore(start) && now.isBefore(start.plusHours(Spieltag.RUNNING_HOURS))
}

@Composable
private fun RankingPage(clickTt: ClickTtRepository, g: GroupRef, links: Links) {
    var state by remember { mutableStateOf<Result<List<BalanceRow>>?>(null) }
    LaunchedEffect(g) { state = clickTt.ranking(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Bilanz-Rangliste") }
        item { InfoText(g.name) }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value.orEmpty()) { r ->
            val team = r.teamId.takeIf { it.isNotBlank() }?.let { TeamRef(g, it, r.team, r.clubNr) }
            AppCard(onClick = if (r.playerId.startsWith("NU")) ({ links.player(PlayerLink(r.playerId, r.name, clubNr = r.clubNr, team = team)) }) else null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RankBadge(r.rank)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(r.team, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill(r.rating, color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.positions.forEach { (p, v) -> Pill("$p: $v") }
                }
                Text("Gesamt ${r.won}:${r.lost}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PickTeamPage(clickTt: ClickTtRepository, g: GroupRef, onPick: (String, String) -> Unit) {
    var state by remember { mutableStateOf<Result<LeagueTable>?>(null) }
    LaunchedEffect(g) { state = clickTt.table(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Mannschaft wählen") }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value?.rows.orEmpty()) { r ->
            AppCard(onClick = { onPick(r.teamId, r.teamName) }) { Text(r.teamName, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** TTR-Rangliste der Gruppe: alle gemeldeten Spieler aller Mannschaften, umschaltbar zwischen TTR und QTTR. */
@Composable
private fun TtrRankingPage(clickTt: ClickTtRepository, teams: TeamRepository, players: PlayerRepository, g: GroupRef, links: Links) {
    var progress by remember { mutableStateOf("Lade Mannschaften …") }
    var failed by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<RankEntry>() }
    LaunchedEffect(g) {
        entries.clear()
        val table = clickTt.table(g)
        if (table !is Result.Ok) { failed = (table as? Result.Failed)?.message ?: "Bitte zuerst anmelden."; return@LaunchedEffect }
        val seeds = table.value.rows.flatMap { row ->
            val ref = TeamRef(g, row.teamId, row.teamName)
            ((teams.players(row.teamId) as? Result.Ok)?.value.orEmpty())
                .mapNotNull { m -> m.nuId?.let { RankEntry(it, m.displayName, row.teamName, null, null, null, ref) } }
        }
        loadRatings(players, seeds, onEntry = { entries.add(it) }) { done, total -> progress = "Lade Werte: $done von $total" }
        progress = ""
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("TTR-Rangliste") }
        item { InfoText(g.name) }
        item { RatingModeChips() }
        failed?.let { item { InfoText(it) } }
        if (progress.isNotEmpty() && failed == null) item { InfoText(progress) }
        rankingItems(entries, RatingModeState.mode, links.player)
        item { Spacer(Modifier.height(16.dp)) }
    }
}
