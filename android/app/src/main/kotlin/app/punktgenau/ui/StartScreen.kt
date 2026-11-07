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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun StartScreen(
    ui: StartUi,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenClub: () -> Unit,
    onOpenTransfers: () -> Unit,
) {
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
                        if (p.ttr == null && p.qttr != null) Pill("TTR nicht geliefert")
                        if (last != null) {
                            val (txt, color) = deltaText(last.delta)
                            Pill("Zuletzt $txt", color = color.copy(alpha = 0.12f), textColor = color)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onOpenCalculator, Modifier.weight(1f)) { Text("Rechner") }
                    OutlinedButton(onOpenHistory, Modifier.weight(1f)) { Text("Verlauf") }
                }
                OutlinedButton(onOpenClub, Modifier.fillMaxWidth()) { Text("Mein Verein") }
                OutlinedButton(onOpenTransfers, Modifier.fillMaxWidth()) { Text("Wechselliste") }
                OutlinedButton(onRefresh, Modifier.fillMaxWidth()) { Text("Aktualisieren") }
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
