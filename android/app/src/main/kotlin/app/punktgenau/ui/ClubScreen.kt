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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.Club
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.ClubTeam
import app.punktgenau.data.ClickTtRepository
import app.punktgenau.data.GroupRef
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RankEntry
import app.punktgenau.data.Result
import app.punktgenau.data.TeamContact
import app.punktgenau.data.TeamDetails
import app.punktgenau.data.TeamRef
import app.punktgenau.data.TeamRepository
import app.punktgenau.data.Venue

/** Zustand der Vereinsansicht, der das Öffnen einer Mannschaft oder Person überdauert. */
class ClubViewState {
    var ranking by mutableStateOf(false)
}

/** Verweis auf eine Mannschaft des Vereins in ihrer Gruppe. */
private fun teamRef(t: ClubTeam, association: String, season: String, clubNr: String) =
    TeamRef(GroupRef(t.organization.ifBlank { association }, season, t.groupId, t.league.ifBlank { t.name }), t.teamId, t.name, clubNr)

/** Verein: Kontakt, Spiellokale, Mannschaften mit Mannschaftskontakt, und die Vereinsrangliste. */
@Composable
fun ClubScreen(
    clubs: ClubRepository,
    clickTt: ClickTtRepository,
    teams: TeamRepository,
    players: PlayerRepository,
    clubNr: String,
    association: String,
    season: String,
    view: ClubViewState,
    links: Links,
    onClose: () -> Unit,
) {
    var state by remember { mutableStateOf<Result<Club>?>(null) }
    var ranking by view::ranking
    LaunchedEffect(clubNr, season) { state = clubs.load(clubNr, association, season) }
    BackHandler(onBack = onClose)
    BackHandler(enabled = ranking) { ranking = false }

    val club = (state as? Result.Ok)?.value
    if (ranking && club != null) {
        ClubRankingPage(teams, players, club.teams.map { teamRef(it, association, season, clubNr) }, links, onClose = { ranking = false })
        return
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                when (val s = state) {
                    null -> LoadingBox()
                    is Result.Ok -> {
                        ScreenTitle(s.value.info?.name ?: "Verein")
                        s.value.info?.region?.let { InfoText(it) }
                    }
                    Result.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
                    is Result.Failed -> InfoText(s.message)
                }
            }
            club?.info?.let { info ->
                item {
                    AppCard {
                        SectionLabel("Kontakt")
                        info.contactName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        info.contactPhone?.let { Text(it.trim(), style = MaterialTheme.typography.bodyMedium) }
                        info.contactEmail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (info.venues.isNotEmpty()) {
                            SectionLabel(if (info.venues.size == 1) "Spiellokal" else "Spiellokale")
                            info.venues.forEach { VenueRow(it) }
                        }
                    }
                }
            }
            if (club != null) {
                item {
                    OutlinedButton({ ranking = true }, Modifier.fillMaxWidth()) { Text("Vereinsrangliste") }
                }
            }
            item { SectionLabel("Mannschaften") }
            items(club?.teams.orEmpty()) { t: ClubTeam ->
                val ref = teamRef(t, association, season, clubNr)
                TeamCard(
                    clickTt = clickTt,
                    group = ref.group,
                    team = t,
                    fallbackVenues = club?.info?.venues.orEmpty(),
                    onOpenTeam = { links.team(ref) },
                    onOpenLeague = { links.league(ref.group) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TeamCard(clickTt: ClickTtRepository, group: GroupRef, team: ClubTeam, fallbackVenues: List<Venue>, onOpenTeam: () -> Unit, onOpenLeague: () -> Unit) {
    var details by remember(team.teamId) { mutableStateOf<TeamDetails?>(null) }
    var loading by remember(team.teamId) { mutableStateOf(true) }
    LaunchedEffect(team.teamId, group.season) {
        loading = true
        details = (clickTt.teamDetails(group, team.teamId) as? Result.Ok)?.value
        loading = false
    }
    AppCard(onClick = onOpenTeam) {
        Text(team.name, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(team.league, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onOpenLeague) { Text("Liga") }
        }
        val d = details
        when {
            loading -> InfoText("Lade Kontakt und Spiellokal …")
            d == null -> InfoText("Kontakt und Spiellokal nicht verfügbar.")
            else -> {
                d.contact?.let { ContactLine(it) }
                val homeVenues = d.homeVenues.ifEmpty { fallbackVenues }
                if (homeVenues.isNotEmpty()) {
                    SectionLabel(if (d.homeVenues.isNotEmpty()) "Spiellokal" else "Spiellokal (Verein)")
                    homeVenues.forEach { VenueRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ContactLine(c: TeamContact) {
    Column {
        SectionLabel("Mannschaftskontakt")
        Text(
            listOfNotNull(c.name, c.phone?.trim()?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "–" },
            style = MaterialTheme.typography.bodyMedium,
        )
        c.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun VenueRow(v: Venue) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (v.name.isNotBlank()) Text(v.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(v.query, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton({ openNavigation(context, v.query) }) { Text("Navigation") }
    }
}

/**
 * Vereinsrangliste: die Spieler aus den Mannschaftsaufstellungen des Vereins (dedupliziert), umschaltbar
 * zwischen TTR und QTTR. Spieler, die in keiner Aufstellung stehen, fehlen.
 */
@Composable
private fun ClubRankingPage(teams: TeamRepository, players: PlayerRepository, clubTeams: List<TeamRef>, links: Links, onClose: () -> Unit) {
    var progress by remember { mutableStateOf("Lade Mannschaftsaufstellungen …") }
    var failed by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<RankEntry>() }
    LaunchedEffect(clubTeams) {
        entries.clear()
        failed = null
        val seen = LinkedHashMap<String, RankEntry>()
        var needsLogin = false
        for (t in clubTeams) {
            when (val r = teams.players(t.teamId)) {
                is Result.Ok -> r.value.forEach { m ->
                    val nu = m.nuId ?: return@forEach
                    seen.putIfAbsent(nu, RankEntry(nu, m.displayName, t.name, null, null, t.clubNr, t))
                }
                Result.NeedsLogin -> needsLogin = true
                is Result.Failed -> Unit
            }
        }
        if (seen.isEmpty()) {
            failed = if (needsLogin) "Bitte zuerst anmelden." else "Keine Spieler in den Aufstellungen gefunden."
            progress = ""
            return@LaunchedEffect
        }
        loadRatings(players, seen.values.toList(), onEntry = { entries.add(it) }) { done, total -> progress = "Lade Werte: $done von $total" }
        progress = ""
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück zum Verein") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ScreenTitle("Vereinsrangliste") }
            item { InfoText("Spieler aus den Mannschaftsaufstellungen des Vereins.") }
            item { RatingModeChips() }
            failed?.let { msg -> item { InfoText(msg) } }
            if (progress.isNotEmpty() && failed == null) item { InfoText(progress) }
            rankingItems(entries, RatingModeState.mode, links.player)
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
