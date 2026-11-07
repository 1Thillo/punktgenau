package app.punktgenau.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun StartScreen(ui: StartUi, onLogin: () -> Unit, onRefresh: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (ui) {
            StartUi.Loading -> CircularProgressIndicator()
            StartUi.LoggedOut -> {
                Text("Anmeldung nötig", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Du meldest dich auf der Website von mytischtennis.de an. Dein Passwort bleibt dort und wird nicht in der App gespeichert.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onLogin, Modifier.fillMaxWidth()) { Text("Bei mytischtennis.de anmelden") }
            }
            is StartUi.Error -> {
                Text(ui.message, style = MaterialTheme.typography.bodyLarge)
                Button(onRefresh) { Text("Erneut versuchen") }
                OutlinedButton(onLogin) { Text("Neu anmelden") }
            }
            is StartUi.Shown -> {
                val p = ui.profile
                Text(if (p.name != null) "Hallo, ${p.name}" else "Hallo", style = MaterialTheme.typography.headlineSmall)
                val main = p.ttr ?: p.qttr
                Text(main?.toString() ?: "–", fontSize = 72.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        p.ttr != null -> "TTR"
                        p.qttr != null -> "Q-TTR (TTR wird vom Server nicht geliefert)"
                        else -> "Keine Werte gefunden"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (p.ttr != null && p.qttr != null) Text("Q-TTR ${p.qttr}", style = MaterialTheme.typography.bodyLarge)
                Text("Angemeldet", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onRefresh) { Text("Aktualisieren") }
                OutlinedButton(onLogout) { Text("Abmelden") }
            }
        }
    }
}
