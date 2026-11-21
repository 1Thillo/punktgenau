package app.punktgenau.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.FavoriteStore
import app.punktgenau.data.GroupRef
import app.punktgenau.data.MatchFinder
import app.punktgenau.data.PlayerHistoryOutcome
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.Result
import app.punktgenau.data.Season
import app.punktgenau.data.TeamRef
import app.punktgenau.data.TeamRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Ergebnis der Suche nach den Mannschaften einer Person. */
private sealed interface TeamSearch {
    data object Idle : TeamSearch
    data object Busy : TeamSearch
    data class Found(val teams: List<TeamRef>) : TeamSearch
    data class Failed(val message: String) : TeamSearch
}

/**
 * Sucht die Mannschaften des Vereins, in deren Aufstellung die Person steht. Kostet einen Abruf je Mannschaft
 * des Vereins (Aufstellungen bleiben 30 Minuten im Zwischenspeicher) und läuft deshalb erst auf Wunsch.
 */
private suspend fun findTeams(clubs: ClubRepository, teams: TeamRepository, clubNr: String, association: String, nuId: String): TeamSearch {
    val season = Season.label(LocalDate.now())
    val club = when (val r = clubs.load(clubNr, association, season)) {
        is Result.Ok -> r.value
        Result.NeedsLogin -> return TeamSearch.Failed("Bitte zuerst anmelden.")
        is Result.Failed -> return TeamSearch.Failed(r.message)
    }
    val found = ArrayList<TeamRef>()
    for (t in club.teams) {
        when (val r = teams.players(t.teamId)) {
            is Result.Ok -> if (r.value.any { it.nuId == nuId }) {
                found.add(TeamRef(GroupRef(t.organization.ifBlank { association }, season, t.groupId, t.league.ifBlank { t.name }), t.teamId, t.name, clubNr))
            }
            Result.NeedsLogin -> return TeamSearch.Failed("Bitte zuerst anmelden.")
            is Result.Failed -> Unit
        }
    }
    return if (found.isEmpty()) TeamSearch.Failed("In den Aufstellungen des Vereins nicht gefunden.") else TeamSearch.Found(found)
}

/**
 * Ansicht aus Sicht eines anderen Spielers: dessen öffentliche TTR-Werte und Verlauf (Liste und Diagramm),
 * dazu die Wege zu seinem Verein und seiner Mannschaft. Es wird nicht als dieser Spieler angemeldet.
 */
@Composable
fun PlayerViewScreen(
    repo: PlayerRepository,
    clubs: ClubRepository,
    teams: TeamRepository,
    finder: MatchFinder,
    player: PlayerLink,
    links: Links,
    favorites: FavoriteStore,
    onClose: () -> Unit,
    onUseInCalculator: (Int) -> Unit,
) {
    var outcome by remember { mutableStateOf<PlayerHistoryOutcome?>(null) }
    var isFavorite by remember(player.nuId) { mutableStateOf(favorites.contains(player.nuId)) }
    // Vereinsnummer: aus dem Zusammenhang (Rangliste, Mannschaft, Spielbericht), sonst über die Vereinssuche nach dem Namen.
    var clubNr by remember { mutableStateOf(player.clubNr ?: player.team?.clubNr) }
    var association by remember { mutableStateOf(player.team?.group?.association ?: "RTTVR") }
    var teamSearch by remember { mutableStateOf<TeamSearch>(TeamSearch.Idle) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(player.nuId) { outcome = repo.history(player.nuId) }
    // Vereinsname: aus dem Verweis, sonst aus dem Verlauf der Person.
    val clubName = player.club ?: (outcome as? PlayerHistoryOutcome.Ok)?.history?.clubName
    LaunchedEffect(clubName) {
        if (clubNr == null) clubName?.let { name ->
            clubs.findClub(name)?.let { clubNr = it.clubNr; association = it.association }
        }
    }
    // Findet die Suche genau eine Mannschaft, merkt sich ein Favorit diese; beim nächsten Öffnen gibt es dann den direkten Verweis.
    LaunchedEffect(teamSearch) {
        val found = (teamSearch as? TeamSearch.Found)?.teams?.singleOrNull()
        if (found != null && isFavorite) favorites.add(player.copy(club = clubName, clubNr = clubNr, team = found))
    }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackBar(onClose) {
            AccentAction(
                if (isFavorite) "★ Favorit entfernen" else "☆ Als Favorit merken",
                {
                    if (isFavorite) {
                        favorites.remove(player.nuId)
                    } else {
                        val team = player.team ?: (teamSearch as? TeamSearch.Found)?.teams?.singleOrNull()
                        favorites.add(player.copy(club = clubName, clubNr = clubNr, team = team))
                    }
                    isFavorite = !isFavorite
                },
            )
        }
        ScreenTitle("Sicht von ${player.name}")
        // Wege zu Verein und Mannschaft: eine waagerecht scrollbare Reihe, damit der Verlauf Platz behält.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            clubNr?.let { nr -> AccentAction("Verein ›", { links.club(nr) }) }
            val known = player.team
            if (known != null) {
                AccentAction("Mannschaft: ${known.name} ›", { links.team(known) })
            } else {
                val nr = clubNr
                when (val s = teamSearch) {
                    TeamSearch.Idle -> if (nr != null) {
                        AccentAction("Mannschaft suchen", { teamSearch = TeamSearch.Busy; scope.launch { teamSearch = findTeams(clubs, teams, nr, association, player.nuId) } })
                    }
                    TeamSearch.Busy -> InfoText("Suche in den Aufstellungen des Vereins …")
                    is TeamSearch.Failed -> InfoText(s.message)
                    is TeamSearch.Found -> s.teams.forEach { t ->
                        AccentAction("Mannschaft: ${t.name} (${t.group.name}) ›", { links.team(t) })
                    }
                }
            }
        }
        when (val o = outcome) {
            null -> LoadingBox()
            PlayerHistoryOutcome.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
            is PlayerHistoryOutcome.Failed -> InfoText(o.message)
            is PlayerHistoryOutcome.Ok -> {
                val h = o.history
                val value = h.ttr ?: h.qttr
                HeroCard {
                    Text(
                        (if (h.ttr != null) "TTR" else "Q-TTR") + (clubName?.let { " · ${it.uppercase()}" }.orEmpty()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(value?.toString() ?: "–", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Column(Modifier.weight(1f)) {
                            h.qttr?.takeIf { h.ttr != null }?.let { HeroStat("Q-TTR", it.toString()) }
                        }
                        if (value != null) HeroChoice("Im Rechner als\nmein Wert", { onUseInCalculator(value) })
                    }
                    Text("Öffentliche Werte dieses Spielers. Du bist dabei nicht als er angemeldet.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
                // Derselbe Verlaufs-Screen wie beim eigenen Konto: Spiele und Diagramm.
                HistoryScreen(
                    events = h.events, loggedIn = true, modifier = Modifier.weight(1f).fillMaxWidth(), showTitle = false,
                    findReport = { e ->
                        // Mannschaft aus dem Zusammenhang, sonst alle Mannschaften des Vereins (deren Spielpläne).
                        val candidates = listOfNotNull(player.team).ifEmpty {
                            clubNr?.let { nr ->
                                (clubs.load(nr, association, Season.label(LocalDate.now())) as? Result.Ok)?.value?.teams?.map { t ->
                                    TeamRef(GroupRef(t.organization.ifBlank { association }, Season.label(LocalDate.now()), t.groupId, t.league.ifBlank { t.name }), t.teamId, t.name, nr)
                                }
                            }.orEmpty()
                        }
                        finder.find(e, candidates)
                    },
                    onOpenReport = links.report,
                )
            }
        }
    }
}
