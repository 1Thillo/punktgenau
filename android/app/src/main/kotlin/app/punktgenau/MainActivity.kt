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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.punktgenau.data.Loaded
import app.punktgenau.data.PlayerHit
import app.punktgenau.data.Season
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

@Composable
private fun Root(app: PunktgenauApp, mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    var tab by remember { mutableStateOf(Tab.START) }
    var showLogin by remember { mutableStateOf(false) }
    var showAppointments by remember { mutableStateOf(false) }
    var clubNr by remember { mutableStateOf<String?>(null) }
    var showTransfers by remember { mutableStateOf(false) }
    var showSpieltag by remember { mutableStateOf(false) }
    var showFollow by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var viewPlayer by remember { mutableStateOf<PlayerHit?>(null) }
    var openLeague by remember { mutableStateOf<app.punktgenau.data.GroupRef?>(null) }
    var ui by remember { mutableStateOf<StartUi>(StartUi.Loading) }
    val scope = rememberCoroutineScope()
    val calc = remember { CalculatorState() }

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

    LaunchedEffect(ui) {
        (ui as? StartUi.Shown)?.profile?.let { p -> (p.ttr ?: p.qttr)?.let { if (calc.ownText.isBlank()) calc.ownText = it.toString() } }
    }

    LaunchedEffect(Unit) {
        if (app.jar.hasAuthCookie()) refresh() else ui = StartUi.LoggedOut
    }

    // Zurück-Geste: stufenweise zurück zum Start, erst dort verlässt sie die App.
    val canGoBack = showLogin || showAppointments || showTransfers || showSpieltag || showFollow || report != null || viewPlayer != null || openLeague != null || clubNr != null || tab != Tab.START
    BackHandler(enabled = canGoBack) {
        when {
            showLogin -> showLogin = false
            report != null -> report = null
            showFollow -> showFollow = false
            showSpieltag -> showSpieltag = false
            openLeague != null -> openLeague = null
            clubNr != null -> clubNr = null
            viewPlayer != null -> viewPlayer = null
            showAppointments -> { showAppointments = false; tab = Tab.CALCULATOR }
            showTransfers -> showTransfers = false
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

    openLeague?.let { g ->
        LeagueScreen(app.clickTt, app.teams, app.playerRepo, loggedIn = true, initial = g, onBack = { openLeague = null })
        return
    }

    if (showTransfers) {
        TransferScreen(app.transfers, association = "RTTVR", onClose = { showTransfers = false })
        return
    }

    report?.let { (url, title, live) ->
        SpielberichtScreen(app.spielbericht, url = url, title = title, live = live, onClose = { report = null })
        return
    }

    if (showFollow) {
        FollowScreen(app.clubs, app.follow, onClose = { showFollow = false })
        return
    }

    if (showSpieltag) {
        SpieltagScreen(
            app.spieltag,
            onClose = { showSpieltag = false },
            onNeedsLogin = { showSpieltag = false; ui = StartUi.LoggedOut; tab = Tab.START },
            onOpenFollow = { showFollow = true },
            onOpenReport = { url, title, live -> report = Triple(url, title, live) },
        )
        return
    }

    clubNr?.let { nr ->
        ClubScreen(
            clubs = app.clubs,
            clickTt = app.clickTt,
            teams = app.teams,
            players = app.playerRepo,
            clubNr = nr,
            association = "RTTVR",
            season = Season.label(java.time.LocalDate.now()),
            onClose = { clubNr = null },
            onOpenLeague = { openLeague = it },
        )
        return
    }

    // Liegt über dem Verein-Screen nicht, sondern darunter: Schließen des Vereins führt zurück hierher.
    viewPlayer?.let { p ->
        PlayerViewScreen(
            app.playerRepo,
            clubs = app.clubs,
            nuId = p.nuId.orEmpty(),
            name = p.displayName,
            club = p.club,
            onClose = { viewPlayer = null },
            onUseInCalculator = { value ->
                calc.ownText = value.toString()
                viewPlayer = null
                tab = Tab.CALCULATOR
            },
            onOpenClub = { ref -> clubNr = ref.clubNr },
        )
        return
    }

    if (showAppointments) {
        AppointmentScreen(
            teams = app.teams,
            players = app.playerRepo,
            ownNuId = (ui as? StartUi.Shown)?.profile?.nuId,
            onAddOpponent = { calc.addOpponent(it) },
            onSetOwn = { calc.ownText = it.toString() },
            onClose = { showAppointments = false; tab = Tab.CALCULATOR },
            onNeedsLogin = { showAppointments = false; ui = StartUi.LoggedOut; tab = Tab.START },
        )
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
                    onOpenClub = { scope.launch { clubNr = app.teams.ownClubNr() ?: clubNr } },
                    onOpenTransfers = { showTransfers = true },
                    onOpenSpieltag = { showSpieltag = true },
                )
                Tab.HISTORY -> HistoryScreen(
                    events = (ui as? StartUi.Shown)?.profile?.events.orEmpty(),
                    loggedIn = ui is StartUi.Shown,
                )
                Tab.CALCULATOR -> CalculatorScreen(
                    calc,
                    onLoadAppointment = { showAppointments = true },
                    onResetOwn = {
                        (ui as? StartUi.Shown)?.profile?.let { p -> (p.ttr ?: p.qttr)?.let { calc.ownText = it.toString() } }
                    },
                )
                Tab.LEAGUE -> LeagueScreen(app.clickTt, app.teams, app.playerRepo, loggedIn = ui is StartUi.Shown)
                Tab.SEARCH -> SearchScreen(
                    app.playerRepo,
                    advanced = app.advancedSearch,
                    onViewPlayer = { viewPlayer = it },
                    onAddToCalculator = { calc.addOpponent(it); tab = Tab.CALCULATOR },
                    onNeedsLogin = { ui = StartUi.LoggedOut; tab = Tab.START },
                )
            }
        }
    }
}
