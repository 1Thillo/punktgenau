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
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.Result
import app.punktgenau.data.TeamContact
import app.punktgenau.data.TeamDetails
import app.punktgenau.data.TeamRepository
import app.punktgenau.data.Venue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    onClose: () -> Unit,
    onOpenLeague: (GroupRef) -> Unit,
) {
    var state by remember { mutableStateOf<Result<Club>?>(null) }
    var ranking by remember { mutableStateOf(false) }
    LaunchedEffect(clubNr, season) { state = clubs.load(clubNr, association, season) }
    BackHandler(enabled = ranking) { ranking = false }

    val club = (state as? Result.Ok)?.value
    if (ranking && club != null) {
        ClubRankingPage(teams, players, club.teams, onClose = { ranking = false })
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
                val group = GroupRef(t.organization.ifBlank { association }, season, t.groupId, t.league.ifBlank { t.name })
                TeamCard(
                    clickTt = clickTt,
                    group = group,
                    team = t,
                    fallbackVenues = club?.info?.venues.orEmpty(),
                    onOpenLeague = { onOpenLeague(group) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TeamCard(clickTt: ClickTtRepository, group: GroupRef, team: ClubTeam, fallbackVenues: List<Venue>, onOpenLeague: () -> Unit) {
    var details by remember(team.teamId) { mutableStateOf<TeamDetails?>(null) }
    var loading by remember(team.teamId) { mutableStateOf(true) }
    LaunchedEffect(team.teamId, group.season) {
        loading = true
        details = (clickTt.teamDetails(group, team.teamId) as? Result.Ok)?.value
        loading = false
    }
    AppCard(onClick = onOpenLeague) {
        Text(team.name, style = MaterialTheme.typography.titleMedium)
        Text(team.league, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private data class ClubRankEntry(val name: String, val team: String, val value: Int, val isTtr: Boolean)

/**
 * Vereinsrangliste: die Spieler aus den Mannschaftsaufstellungen des Vereins (dedupliziert),
 * je aktueller TTR, sonst Q-TTR. Spieler, die in keiner Aufstellung stehen, fehlen.
 */
@Composable
private fun ClubRankingPage(teams: TeamRepository, players: PlayerRepository, clubTeams: List<ClubTeam>, onClose: () -> Unit) {
    var progress by remember { mutableStateOf("Lade Mannschaftsaufstellungen …") }
    var failed by remember { mutableStateOf<String?>(null) }
    val entries = remember { mutableStateListOf<ClubRankEntry>() }
    LaunchedEffect(clubTeams) {
        entries.clear()
        failed = null
        val seen = LinkedHashMap<String, Pair<String, String>>()
        var needsLogin = false
        for (t in clubTeams) {
            when (val r = teams.players(t.teamId)) {
                is Result.Ok -> r.value.forEach { m ->
                    val nu = m.nuId ?: return@forEach
                    seen.putIfAbsent(nu, m.displayName to t.name)
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
        val gate = Semaphore(5)
        var done = 0
        coroutineScope {
            seen.map { (nu, info) ->
                async {
                    gate.withPermit {
                        val r = players.ratings(nu)
                        if (r is RatingOutcome.Values) {
                            val v = r.ttr ?: r.qttr
                            if (v != null) entries.add(ClubRankEntry(info.first, info.second, v, r.ttr != null))
                        }
                        done++
                        progress = "Lade Werte: $done von ${seen.size}"
                    }
                }
            }.awaitAll()
        }
        progress = ""
    }
    val sorted = entries.sortedByDescending { it.value }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück zum Verein") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ScreenTitle("Vereinsrangliste") }
            item { InfoText("Spieler aus den Mannschaftsaufstellungen, nach aktuellem TTR (Q-TTR, wenn kein TTR geliefert wird).") }
            failed?.let { msg -> item { InfoText(msg) } }
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
}
