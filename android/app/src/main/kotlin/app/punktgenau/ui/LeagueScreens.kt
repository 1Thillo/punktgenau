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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
    onOpenTransfers: (() -> Unit)? = null,
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
            BackBar({ if (nav == Nav.Home || (initial != null && nav == Nav.Hub(initial))) onBack?.invoke() else nav = back(nav) })
        }
        when (val n = nav) {
            Nav.Home -> LeagueHome(
                myTeams, loggedIn, onOpen = { nav = Nav.Hub(it) }, onOpenTeam = links.team, onChoose = { nav = Nav.Districts },
                onOpenTransfers = onOpenTransfers,
            )
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
private fun LeagueHome(
    myTeams: List<TeamInfo>,
    loggedIn: Boolean,
    onOpen: (GroupRef) -> Unit,
    onOpenTeam: (TeamRef) -> Unit,
    onChoose: () -> Unit,
    onOpenTransfers: (() -> Unit)?,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Liga") }
        item {
            HeroCard {
                Text("MEINE LIGEN", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text(
                    if (myTeams.isEmpty()) "–" else myTeams.size.toString(),
                    fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
                Text(
                    when {
                        myTeams.isNotEmpty() -> if (myTeams.size == 1) "Mannschaft in dieser Saison" else "Mannschaften in dieser Saison"
                        loggedIn -> "Lade deine Mannschaften …"
                        else -> "Anmelden, um deine Ligen zu sehen."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        if (myTeams.isNotEmpty()) item { SectionLabel("Liga öffnen") }
        items(myTeams) { t ->
            val org = t.organization; val gid = t.groupId; val s = t.season
            ListCard(
                title = t.league ?: t.name,
                subtitle = t.name,
                onClick = { if (org != null && gid != null && s != null) onOpen(GroupRef(org, s, gid, t.league ?: t.name)) },
                leading = { LetterBadge(t.league ?: t.name) },
                // Karte öffnet die Liga, die Akzent-Aktion rechts die eigene Mannschaft.
                trailing = if (org != null && gid != null && s != null) ({
                    AccentAction("Mannschaft", { onOpenTeam(TeamRef(GroupRef(org, s, gid, t.league ?: t.name), t.id, t.name)) })
                }) else ({ Chevron() }),
            )
        }
        item { SectionLabel("Entdecken") }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile("Liga auswählen", "Bezirk und Spielklasse", onChoose, Modifier.weight(1f).fillMaxHeight())
                if (onOpenTransfers != null) ChoiceTile("Wechselliste", "Wechsel im Verband", onOpenTransfers, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DistrictPage(clickTt: ClickTtRepository, association: String, season: String, onPick: (District) -> Unit) {
    var state by remember { mutableStateOf<Result<List<District>>?>(null) }
    LaunchedEffect(association, season) { state = clickTt.districts(association, season) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Bezirk wählen") }
        item { InfoText("$association · Saison $season") }
        item { Loaded(state) { } }
        val list = (state as? Result.Ok)?.value.orEmpty()
        items(list) { d -> ListCard(d.name, onClick = { onPick(d) }, leading = { LetterBadge(d.name) }) }
        item { Spacer(Modifier.height(16.dp)) }
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
        if (shown.isNotEmpty()) item { SectionLabel("${shown.size} Spielklassen") }
        items(shown) { g -> ListCard(g.name, onClick = { onPick(g) }) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HubPage(g: GroupRef, onTable: () -> Unit, onSchedule: () -> Unit, onRanking: () -> Unit, onTeams: () -> Unit, onTtr: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Text("LIGA · SAISON ${g.season}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text(g.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(g.association, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
        }
        item { SectionLabel("Überblick") }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile("Tabelle", "Platzierung, Spiele, Punkte", onTable, Modifier.weight(1f).fillMaxHeight())
                ChoiceTile("Spielplan", "Vor- und Rückrunde", onSchedule, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item { SectionLabel("Spieler und Mannschaften") }
        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile("Bilanz-Rangliste", "Beste Spieler nach Bilanz", onRanking, Modifier.weight(1f).fillMaxHeight())
                ChoiceTile("Mannschaften", "Rangliste, Spielplan, Bilanzen", onTeams, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item { ChoiceTile("TTR-Rangliste", "Alle Spieler der Gruppe nach TTR oder QTTR (lädt einige Sekunden)", onTtr, Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun RankBadge(rank: Int, highlight: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                rank.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                color = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Kopf einer Unterseite: Titel und darunter die Gruppe. */
@Composable
private fun PageHead(title: String, sub: String?) {
    Column {
        ScreenTitle(title)
        sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun TablePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>, links: Links) {
    var state by remember { mutableStateOf<Result<LeagueTable>?>(null) }
    LaunchedEffect(g) { state = clickTt.table(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHead("Tabelle", g.name) }
        item {
            Loaded(state) { t ->
                HeroCard {
                    HeroStrip {
                        HeroStat("Mannschaften", t.rows.size.toString())
                        HeroStat("Ø TTR", t.ttrAverage?.toString() ?: "–")
                        HeroStat("Spitze", t.rows.firstOrNull()?.let { "${it.pointsWon}:${it.pointsLost}" } ?: "–")
                    }
                }
            }
        }
        val table = (state as? Result.Ok)?.value
        if (!table?.rows.isNullOrEmpty()) item { SectionLabel("Platz · Mannschaft · Punkte") }
        items(table?.rows.orEmpty()) { r ->
            val mine = r.teamId in own
            ListCard(
                title = r.teamName,
                subtitle = "${r.meetings} Spiele · ${r.won} S ${r.tie} U ${r.lost} N · Spiele ${r.matchesWon}:${r.matchesLost}",
                onClick = { links.team(TeamRef(g, r.teamId, r.teamName)) },
                leading = { RankBadge(r.rank, mine) },
                trailing = { TrailingNumber("${r.pointsWon}:${r.pointsLost}", if (mine) "Deine" else "Punkte", if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SchedulePage(clickTt: ClickTtRepository, g: GroupRef, own: Set<String>, links: Links) {
    var filter by remember { mutableStateOf("gesamt") }
    var state by remember { mutableStateOf<Result<List<GroupMeeting>>?>(null) }
    LaunchedEffect(g, filter) { state = null; state = clickTt.schedule(g, filter) }
    val keys = listOf("gesamt", "vr", "rr")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHead("Spielplan", g.name) }
        item { Segmented(listOf("Gesamt", "Vorrunde", "Rückrunde"), keys.indexOf(filter)) { filter = keys[it] } }
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
            MeetingCard(m.date, m.home, m.away, m.result, mine, running, m.live, report?.let { r -> { links.report(r) } })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Spiel im Spielplan: Datum links, Heim und Gast untereinander, Ergebnis rechts. */
@Composable
private fun MeetingCard(date: String?, home: String, away: String, result: String?, mine: Boolean, running: Boolean, live: Boolean, onClick: (() -> Unit)?) {
    val day = MatchTime.day(date)
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (day != null) {
                DateBadge("%02d.%02d.".format(day.dayOfMonth, day.monthValue), MatchTime.time(date) ?: (day.year % 100).toString())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(home, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(away, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (mine || running) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (running) LivePill(if (live) "LIVE" else "läuft")
                        if (mine) Pill("Eigene", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (result != null) ScoreBox(result) else if (day == null) Text(date.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
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
        item { PageHead("Bilanz-Rangliste", g.name) }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value.orEmpty()) { r ->
            val team = r.teamId.takeIf { it.isNotBlank() }?.let { TeamRef(g, it, r.team, r.clubNr) }
            ListCard(
                title = r.name,
                subtitle = r.team,
                onClick = if (r.playerId.startsWith("NU")) ({ links.player(PlayerLink(r.playerId, r.name, clubNr = r.clubNr, team = team)) }) else null,
                leading = { RankBadge(r.rank) },
                trailing = { TrailingNumber(r.rating, "Gesamt ${r.won}:${r.lost}", MaterialTheme.colorScheme.primary) },
                extra = if (r.positions.isNotEmpty()) ({
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.positions.forEach { (p, v) -> Pill("$p: $v") }
                    }
                }) else null,
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PickTeamPage(clickTt: ClickTtRepository, g: GroupRef, onPick: (String, String) -> Unit) {
    var state by remember { mutableStateOf<Result<LeagueTable>?>(null) }
    LaunchedEffect(g) { state = clickTt.table(g) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHead("Mannschaft wählen", g.name) }
        item { Loaded(state) { } }
        items((state as? Result.Ok)?.value?.rows.orEmpty()) { r ->
            ListCard(r.teamName, onClick = { onPick(r.teamId, r.teamName) }, leading = { LetterBadge(r.teamName) })
        }
        item { Spacer(Modifier.height(16.dp)) }
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
        item { PageHead("TTR-Rangliste", g.name) }
        item { RatingModeChips() }
        failed?.let { item { InfoText(it) } }
        if (progress.isNotEmpty() && failed == null) item { InfoText(progress) }
        rankingItems(entries, RatingModeState.mode, links.player)
        item { Spacer(Modifier.height(16.dp)) }
    }
}
