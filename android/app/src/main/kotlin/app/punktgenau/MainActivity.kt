package app.punktgenau

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import app.punktgenau.ui.AppointmentScreen
import app.punktgenau.ui.CalculatorScreen
import app.punktgenau.ui.CalculatorState
import app.punktgenau.ui.HistoryScreen
import app.punktgenau.ui.LoginScreen
import app.punktgenau.ui.PunktgenauTheme
import app.punktgenau.ui.SearchScreen
import app.punktgenau.ui.StartScreen
import app.punktgenau.ui.StartUi
import app.punktgenau.ui.ThemeMode
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
    SEARCH("Suche", Icons.Filled.Search),
}

@Composable
private fun Root(app: PunktgenauApp, mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    var tab by remember { mutableStateOf(Tab.START) }
    var showLogin by remember { mutableStateOf(false) }
    var showAppointments by remember { mutableStateOf(false) }
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
        (ui as? StartUi.Shown)?.profile?.let { p -> (p.ttr ?: p.qttr)?.let { calc.ownText = it.toString() } }
    }

    LaunchedEffect(Unit) {
        if (app.jar.hasAuthCookie()) refresh() else ui = StartUi.LoggedOut
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
                )
                Tab.HISTORY -> HistoryScreen(
                    events = (ui as? StartUi.Shown)?.profile?.events.orEmpty(),
                    loggedIn = ui is StartUi.Shown,
                )
                Tab.CALCULATOR -> CalculatorScreen(calc, onLoadAppointment = { showAppointments = true })
                Tab.SEARCH -> SearchScreen(
                    app.playerRepo,
                    onAddToCalculator = { calc.addOpponent(it); tab = Tab.CALCULATOR },
                    onNeedsLogin = { ui = StartUi.LoggedOut; tab = Tab.START },
                )
            }
        }
    }
}
