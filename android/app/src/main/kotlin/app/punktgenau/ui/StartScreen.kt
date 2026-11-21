package app.punktgenau.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.OwnProfile

sealed interface StartUi {
    data object Loading : StartUi
    data object LoggedOut : StartUi
    data class Shown(val profile: OwnProfile) : StartUi
    data class Error(val message: String) : StartUi
}

/** Startbildschirm. Zum Aktualisieren nach unten ziehen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    ui: StartUi,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenClub: () -> Unit,
    onOpenSpieltag: () -> Unit,
    onOpenFavorites: () -> Unit,
    alertsOn: Boolean,
    onAlertsChange: (Boolean) -> Unit,
) {
    PullToRefreshBox(isRefreshing = ui == StartUi.Loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (ui) {
            StartUi.Loading -> {
                ScreenTitle("punktgenau")
                LoadingBox()
            }
            StartUi.LoggedOut -> {
                ScreenTitle("punktgenau")
                AppCard {
                    Text("Anmeldung nötig", style = MaterialTheme.typography.titleLarge)
                    InfoText("Du meldest dich auf der Website von mytischtennis.de an. Dein Passwort bleibt dort und wird nicht in der App gespeichert.")
                    Spacer(Modifier.height(8.dp))
                    Button(onLogin, Modifier.fillMaxWidth()) { Text("Bei mytischtennis.de anmelden") }
                }
            }
            is StartUi.Error -> {
                ScreenTitle("punktgenau")
                AppCard {
                    Text(ui.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onRefresh, Modifier.fillMaxWidth()) { Text("Erneut versuchen") }
                    OutlinedButton(onLogin, Modifier.fillMaxWidth()) { Text("Neu anmelden") }
                }
            }
            is StartUi.Shown -> {
                val p = ui.profile
                ScreenTitle(if (p.name != null) "Hallo, ${p.name}" else "Hallo")
                val main = p.ttr ?: p.qttr
                AppCard {
                    Text(
                        if (p.ttr != null) "Dein TTR" else "Dein Q-TTR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(main?.toString() ?: "–", fontSize = 84.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val last = p.events.lastOrNull { it.type == "meeting" }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.ttr != null && p.qttr != null) Pill("Q-TTR ${p.qttr}")
                        if (p.ttr == null && p.qttr != null) {
                            Pill(p.qttrDate?.let { "TTR nicht geliefert · Stand $it" } ?: "TTR nicht geliefert")
                        }
                        if (last != null) {
                            val (txt, color) = deltaText(last.delta)
                            Pill("Zuletzt $txt", color = color.copy(alpha = 0.12f), textColor = color)
                        }
                    }
                }
                OutlinedButton(onOpenFavorites, Modifier.fillMaxWidth()) { Text("Favoriten") }
                OutlinedButton(onOpenClub, Modifier.fillMaxWidth()) { Text("Mein Verein") }
                OutlinedButton(onOpenSpieltag, Modifier.fillMaxWidth()) { Text("Spieltag") }
            }
        }
        SectionLabel("Benachrichtigungen")
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Spiele melden", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Kommende Spiele (3 Stunden vorher) und neue Ergebnisse deiner Mannschaften. Die Prüfung läuft alle drei Stunden im Hintergrund.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = alertsOn, onCheckedChange = onAlertsChange)
            }
        }
        SectionLabel("Darstellung")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m -> FilterChip(themeMode == m, { onThemeChange(m) }, { Text(m.label) }) }
        }
        if (ui is StartUi.Shown) {
            SectionLabel("Konto")
            OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("Abmelden und Daten löschen") }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}
