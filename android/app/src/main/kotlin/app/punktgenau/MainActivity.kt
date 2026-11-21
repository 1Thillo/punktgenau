package app.punktgenau

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.punktgenau.data.GroupRef
import app.punktgenau.data.Loaded
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.ReportLink
import app.punktgenau.data.Season
import app.punktgenau.data.TeamRef
import app.punktgenau.ui.ClubViewState
import app.punktgenau.ui.LeagueNavState
import app.punktgenau.ui.Links
import app.punktgenau.ui.SpieltagState
import app.punktgenau.ui.TeamScreen
import app.punktgenau.ui.TeamViewState
import app.punktgenau.ui.AppointmentScreen
import app.punktgenau.ui.CalculatorScreen
import app.punktgenau.ui.CalculatorState
import app.punktgenau.ui.ClubScreen
import app.punktgenau.ui.HistoryScreen
import app.punktgenau.ui.LeagueScreen
import app.punktgenau.ui.LoginScreen
import app.punktgenau.ui.PunktgenauTheme
import app.punktgenau.ui.SearchScreen
import app.punktgenau.ui.StartScreen
import app.punktgenau.ui.StartUi
import app.punktgenau.ui.ThemeMode
import app.punktgenau.ui.PlayerViewScreen
import app.punktgenau.ui.TransferScreen
import app.punktgenau.ui.SpieltagScreen
import app.punktgenau.ui.FollowScreen
import app.punktgenau.ui.SpielberichtScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PunktgenauApp
        setContent {
            var mode by remember { mutableStateOf(app.themeMode) }
            PunktgenauTheme(mode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(app, mode) { mode = it; app.themeMode = it }
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    START("Start", Icons.Filled.Home),
    HISTORY("Verlauf", Icons.AutoMirrored.Filled.List),
    CALCULATOR("Rechner", Icons.Filled.Create),
    LEAGUE("Liga", Icons.Filled.Star),
    SEARCH("Suche", Icons.Filled.Search),
}

/**
 * Ein Bildschirm über den Reitern. Die geöffneten Bildschirme liegen als Stapel übereinander; "Zurück" nimmt
 * den obersten weg. So führt jeder Verweis (Person, Mannschaft, Verein, Liga, Spielbericht) weiter, und der
 * Rückweg geht Schritt für Schritt. Zustand, der den Rückweg überdauern soll, hängt am Eintrag selbst.
 */
private sealed interface Dest {
    data object Appointments : Dest
    data object Transfers : Dest
    data object Follow : Dest
    class Spieltag : Dest { val state = SpieltagState() }
    class Report(val link: ReportLink) : Dest
    class Player(val link: PlayerLink) : Dest
    class Club(val clubNr: String) : Dest { val state = ClubViewState() }
    class League(val group: GroupRef) : Dest { val state = LeagueNavState(group) }
    class Team(val ref: TeamRef) : Dest { val state = TeamViewState() }
}

@Composable
private fun Root(app: PunktgenauApp, mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    var tab by remember { mutableStateOf(Tab.START) }
    var showLogin by remember { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Dest>() }
    val leagueTab = remember { LeagueNavState() }
    var ui by remember { mutableStateOf<StartUi>(StartUi.Loading) }
    val scope = rememberCoroutineScope()
    val calc = remember { CalculatorState() }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
    val links = remember {
        Links(
            player = { stack.add(Dest.Player(it)) },
            team = { stack.add(Dest.Team(it)) },
            club = { stack.add(Dest.Club(it)) },
            league = { stack.add(Dest.League(it)) },
            report = { stack.add(Dest.Report(it)) },
        )
    }

    fun refresh() {
        scope.launch {
            ui = StartUi.Loading
            ui = when (val r = app.profiles.load()) {
                is Loaded.Ok -> StartUi.Shown(r.profile)
                Loaded.NeedsLogin -> StartUi.LoggedOut
                is Loaded.Failed -> StartUi.Error(r.message)
            }
        }
    }

    // Eigene Mannschaften für die Verknüpfung Verlauf → Spielbericht; einmal geladen, dann gemerkt.
    var ownTeams by remember { mutableStateOf<List<TeamRef>?>(null) }
    suspend fun ownTeamRefs(): List<TeamRef> {
        ownTeams?.let { return it }
        val list = (app.teams.myTeams() as? app.punktgenau.data.Result.Ok)?.value.orEmpty().mapNotNull { t ->
            val org = t.organization ?: return@mapNotNull null
            val season = t.season ?: return@mapNotNull null
            val group = t.groupId ?: return@mapNotNull null
            TeamRef(GroupRef(org, season, group, t.league ?: t.name), t.id, t.name)
        }
        if (list.isNotEmpty()) ownTeams = list
        return list
    }

    fun loggedOut() {
        stack.clear()
        ui = StartUi.LoggedOut
        tab = Tab.START
    }

    LaunchedEffect(ui) {
        (ui as? StartUi.Shown)?.profile?.let { p -> (p.ttr ?: p.qttr)?.let { if (calc.ownText.isBlank()) { calc.ownText = it.toString(); calc.ownFallbackValue = if (p.ttr != null) null else p.qttr } } }
    }

    LaunchedEffect(Unit) {
        if (app.jar.hasAuthCookie()) refresh() else ui = StartUi.LoggedOut
    }

    // Zurück-Geste: erst den obersten Bildschirm schließen, dann zum Start-Reiter, erst dort verlässt sie die App.
    BackHandler(enabled = showLogin || stack.isNotEmpty() || tab != Tab.START) {
        when {
            showLogin -> showLogin = false
            stack.isNotEmpty() -> pop()
            else -> tab = Tab.START
        }
    }

    if (showLogin) {
        LoginScreen(
            startUrl = app.baseUrl.toString(),
            onDone = { header ->
                app.session.onLoginCompleted(header)
                showLogin = false
                refresh()
            },
            onCancel = { showLogin = false },
        )
        return
    }

    val top = stack.lastOrNull()
    if (top != null) {
        // key: zwei Bildschirme derselben Art hintereinander (Mannschaft A, dann Mannschaft B) teilen keinen Zustand.
        key(top) {
            when (top) {
                Dest.Appointments -> AppointmentScreen(
                    teams = app.teams,
                    players = app.playerRepo,
                    ownNuId = (ui as? StartUi.Shown)?.profile?.nuId,
                    onAddOpponent = { calc.addOpponent(it) },
                    onSetOwn = { calc.ownText = it.toString(); calc.ownFallbackValue = null },
                    onClose = { pop(); tab = Tab.CALCULATOR },
                    onNeedsLogin = { loggedOut() },
                )
                Dest.Transfers -> TransferScreen(app.transfers, association = "RTTVR", onClose = { pop() })
                Dest.Follow -> FollowScreen(app.clubs, app.follow, onClose = { pop() })
                is Dest.Spieltag -> SpieltagScreen(
                    app.spieltag,
                    state = top.state,
                    links = links,
                    onClose = { pop() },
                    onNeedsLogin = { loggedOut() },
                    onOpenFollow = { stack.add(Dest.Follow) },
                )
                is Dest.Report -> SpielberichtScreen(app.spielbericht, link = top.link, links = links, onClose = { pop() })
                is Dest.Player -> PlayerViewScreen(
                    app.playerRepo,
                    clubs = app.clubs,
                    teams = app.teams,
                    finder = app.matchFinder,
                    player = top.link,
                    links = links,
                    onClose = { pop() },
                    onUseInCalculator = { value ->
                        calc.ownText = value.toString()
                        calc.ownFallbackValue = null
                        stack.clear()
                        tab = Tab.CALCULATOR
                    },
                )
                is Dest.Club -> ClubScreen(
                    clubs = app.clubs,
                    clickTt = app.clickTt,
                    teams = app.teams,
                    players = app.playerRepo,
                    clubNr = top.clubNr,
                    association = "RTTVR",
                    season = Season.label(java.time.LocalDate.now()),
                    view = top.state,
                    links = links,
                    onClose = { pop() },
                )
                is Dest.League -> LeagueScreen(
                    app.clickTt, app.teams, app.playerRepo,
                    loggedIn = ui is StartUi.Shown, state = top.state, links = links, onBack = { pop() },
                )
                is Dest.Team -> TeamScreen(app.clickTt, app.teams, app.playerRepo, team = top.ref, state = top.state, links = links, onClose = { pop() })
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(tab == t, { tab = t }, icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.START -> StartScreen(
                    ui,
                    themeMode = mode,
                    onThemeChange = onMode,
                    onLogin = { showLogin = true },
                    onRefresh = { refresh() },
                    onLogout = { app.session.logout(); ui = StartUi.LoggedOut },
                    onOpenHistory = { tab = Tab.HISTORY },
                    onOpenCalculator = { tab = Tab.CALCULATOR },
                    onOpenClub = { scope.launch { app.teams.ownClubNr()?.let { stack.add(Dest.Club(it)) } } },
                    onOpenTransfers = { stack.add(Dest.Transfers) },
                    onOpenSpieltag = { stack.add(Dest.Spieltag()) },
                )
                Tab.HISTORY -> HistoryScreen(
                    events = (ui as? StartUi.Shown)?.profile?.events.orEmpty(),
                    loggedIn = ui is StartUi.Shown,
                    findReport = { e -> app.matchFinder.find(e, ownTeamRefs()) },
                    onOpenReport = links.report,
                )
                Tab.CALCULATOR -> CalculatorScreen(
                    calc,
                    onLoadAppointment = { stack.add(Dest.Appointments) },
                    onResetOwn = {
                        (ui as? StartUi.Shown)?.profile?.let { p -> (p.ttr ?: p.qttr)?.let { calc.ownText = it.toString(); calc.ownFallbackValue = if (p.ttr != null) null else p.qttr } }
                    },
                )
                Tab.LEAGUE -> LeagueScreen(app.clickTt, app.teams, app.playerRepo, loggedIn = ui is StartUi.Shown, state = leagueTab, links = links)
                Tab.SEARCH -> SearchScreen(
                    app.playerRepo,
                    advanced = app.advancedSearch,
                    onViewPlayer = { hit -> hit.nuId?.let { stack.add(Dest.Player(PlayerLink(it, hit.displayName, club = hit.club))) } },
                    onAddToCalculator = { calc.addOpponent(it); tab = Tab.CALCULATOR },
                    onNeedsLogin = { ui = StartUi.LoggedOut; tab = Tab.START },
                )
            }
        }
    }
}
