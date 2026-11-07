package app.punktgenau

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.punktgenau.data.Loaded
import app.punktgenau.ui.AppointmentScreen
import app.punktgenau.ui.CalculatorScreen
import app.punktgenau.ui.CalculatorState
import app.punktgenau.ui.SearchScreen
import app.punktgenau.ui.LoginScreen
import app.punktgenau.ui.PunktgenauTheme
import app.punktgenau.ui.StartScreen
import app.punktgenau.ui.StartUi
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PunktgenauApp
        setContent {
            PunktgenauTheme {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) { Root(app) }
            }
        }
    }
}

@Composable
private fun Root(app: PunktgenauApp) {
    var tab by remember { mutableStateOf(0) }
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
            onClose = { showAppointments = false; tab = 1 },
            onNeedsLogin = { showAppointments = false; ui = StartUi.LoggedOut; tab = 0 },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = {}, label = { Text("Start") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = {}, label = { Text("Rechner") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = {}, label = { Text("Suche") })
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        androidx.compose.foundation.layout.Box(m) {
            if (tab == 0) {
                StartScreen(
                    ui,
                    onLogin = { showLogin = true },
                    onRefresh = { refresh() },
                    onLogout = { app.session.logout(); ui = StartUi.LoggedOut },
                )
            } else if (tab == 1) {
                CalculatorScreen(calc, onLoadAppointment = { showAppointments = true })
            } else {
                SearchScreen(
                    app.playerRepo,
                    onAddToCalculator = { calc.addOpponent(it); tab = 1 },
                    onNeedsLogin = { ui = StartUi.LoggedOut; tab = 0 },
                )
            }
        }
    }
}
