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
import app.punktgenau.data.PlayerLine
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.Result
import app.punktgenau.data.Season
import app.punktgenau.data.TeamInfo
import app.punktgenau.data.TeamRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

private sealed interface Nav {
    data object Home : Nav
    data object Districts : Nav
    data class Leagues(val district: District) : Nav
    data class Hub(val group: GroupRef) : Nav
    data class Table(val group: GroupRef) : Nav
    data class Schedule(val group: GroupRef) : Nav
    data class Ranking(val group: GroupRef) : Nav
    data class PickTeam(val group: GroupRef) : Nav
    data class TeamPlayers(val group: GroupRef, val teamId: String, val teamName: String) : Nav
    data class TtrRanking(val group: GroupRef) : Nav
}

/** Liga-Reiter: eigene Ligen, Ligenauswahl, Seiten der Gruppe. */
@Composable
fun LeagueScreen(clickTt: ClickTtRepository, teams: TeamRepository, players: PlayerRepository, loggedIn: Boolean) {
    var nav by remember { mutableStateOf<Nav>(Nav.Home) }
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
        is Nav.TeamPlayers -> Nav.PickTeam(n.group)
        is Nav.TtrRanking -> Nav.Hub(n.group)
    }
    BackHandler(enabled = nav != Nav.Home) { nav = back(nav) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        if (nav != Nav.Home) {
            TextButton({ nav = back(nav) }) { Text("Zurück") }
        }
        when (val n = nav) {
            Nav.Home -> LeagueHome(myTeams, loggedIn, onOpen = { nav = Nav.Hub(it) }, onChoose = { nav = Nav.Districts })
            Nav.Districts -> DistrictPage(clickTt, association, season) { nav = Nav.Leagues(it) }
            is Nav.Leagues -> LeaguePickPage(clickTt, association, season, n.district) { g ->
                nav = Nav.Hub(GroupRef(association, season, g.groupId, g.name))
            }
            is Nav.Hub -> HubPage(n.group,
                onTable = { nav = Nav.Table(n.group) }, onSchedule = { nav = Nav.Schedule(n.group) },
                onRanking = { nav = Nav.Ranking(n.group) }, onTeams = { nav = Nav.PickTeam(n.group) },
                onTtr = { nav = Nav.TtrRanking(n.group) })
            is Nav.Table -> TablePage(clickTt, n.group, ownTeamIds)
            is Nav.Schedule -> SchedulePage(clickTt, n.group, ownTeamIds)
            is Nav.Ranking -> RankingPage(clickTt, n.group)
            is Nav.PickTeam -> PickTeamPage(clickTt, n.group) { id, name -> nav = Nav.TeamPlayers(n.group, id, name) }
            is Nav.TeamPlayers -> TeamPlayersPage(clickTt, n.group, n.teamId, n.teamName)
            is Nav.TtrRanking -> TtrRankingPage(clickTt, teams, players, n.group)
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
private fun LeagueHome(myTeams: List<TeamInfo>, loggedIn: Boolean, onOpen: (GroupRef) -> Unit, onChoose: () -> Unit) {
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
        item { HubTile("Aufstellung", "Spieler einer Mannschaft mit Einsätzen", onTeams) }
        item { HubTile("TTR-Rangliste", "Alle Spieler der Gruppe nach TTR (lädt einige Sekunden)", onTtr) }
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
private fun RankBadge(rank: Int, highlight: Boolean = false) {
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
private fun TablePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>) {
    var state by remember { mutableStateOf<Result<LeagueTable>?>(null) }
    LaunchedEffect(g) { state = clickTt.table(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Tabelle") }
        item { InfoText(g.name) }
        item { Loaded(state) { t -> t.ttrAverage?.let { InfoText("Ø TTR der Liga: $it") } } }
        val table = (state as? Result.Ok)?.value
        items(table?.rows.orEmpty()) { r ->
            val mine = r.teamId in own
            AppCard {
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
private fun SchedulePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>) {
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
            AppCard {
                Text(formatShort(m.date), style = MaterialTheme.typography.bodySmall, color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${m.home} – ${m.away}", style = MaterialTheme.typography.titleMedium)
                m.result?.let { Pill(it) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun formatShort(iso: String?): String {
    val d = iso?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return iso.orEmpty()
    val time = iso.drop(11).take(5)
    return "%02d.%02d.%02d".format(d.dayOfMonth, d.monthValue, d.year % 100) + if (time.length == 5) " $time" else ""
}

@Composable
private fun RankingPage(clickTt: ClickTtRepository, g: GroupRef) {
    var state by remember { mutableStateOf<Result<List<BalanceRow>>?>(null) }
    LaunchedEffect(g) { state = clickTt.ranking(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Bilanz-Rangliste") }
        item { InfoText(g.name) }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value.orEmpty()) { r ->
            AppCard {
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

@Composable
private fun TeamPlayersPage(clickTt: ClickTtRepository, g: GroupRef, teamId: String, teamName: String) {
    var state by remember { mutableStateOf<Result<List<PlayerLine>>?>(null) }
    val open = remember { mutableStateListOf<String>() }
    LaunchedEffect(g, teamId) { state = clickTt.teamPlayers(g, teamId) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle(teamName) }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value.orEmpty()) { p ->
            val expanded = p.playerId in open
            AppCard(onClick = { if (expanded) open.remove(p.playerId) else open.add(p.playerId) }) {
                Text("${p.position}  ${p.name}", style = MaterialTheme.typography.titleMedium)
                if (expanded) {
                    Text("Einsätze: ${p.appearances}", style = MaterialTheme.typography.bodyMedium)
                    Text("Gesamt: ${p.won}:${p.lost}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    p.perOpponent.forEach { o -> Text("Gegen Position ${o.opponentRank}: ${o.won}:${o.lost}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private data class TtrEntry(val name: String, val team: String, val value: Int, val isTtr: Boolean)

@Composable
private fun TtrRankingPage(clickTt: ClickTtRepository, teams: TeamRepository, players: PlayerRepository, g: GroupRef) {
    var progress by remember { mutableStateOf("Lade Mannschaften …") }
    var failed by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<TtrEntry>() }
    LaunchedEffect(g) {
        entries.clear()
        val table = clickTt.table(g)
        if (table !is Result.Ok) { failed = (table as? Result.Failed)?.message ?: "Bitte zuerst anmelden."; return@LaunchedEffect }
        val members = table.value.rows.flatMap { row ->
            ((teams.players(row.teamId) as? Result.Ok)?.value.orEmpty()).filter { it.nuId != null }.map { row.teamName to it }
        }
        val gate = Semaphore(5)
        var done = 0
        run {
            members.map { (team, m) ->
                async {
                    gate.withPermit {
                        val r = players.ratings(m.nuId!!)
                        if (r is RatingOutcome.Values) {
                            val v = r.ttr ?: r.qttr
                            if (v != null) entries.add(TtrEntry(m.displayName, team, v, r.ttr != null))
                        }
                        done++
                        progress = "Lade Werte: $done von ${members.size}"
                    }
                }
            }.awaitAll()
        }
        progress = ""
    }
    val sorted = entries.sortedByDescending { it.value }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("TTR-Rangliste") }
        item { InfoText(g.name) }
        failed?.let { item { InfoText(it) } }
        if (progress.isNotEmpty() && failed == null) item { InfoText(progress) }
        items(sorted.size) { i ->
            val e = sorted[i]
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RankBadge(i + 1)
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium)
                        Text(e.team, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(e.value.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (!e.isTtr) Text("Q-TTR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
