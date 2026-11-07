package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.ClubRef
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.PlayerHistoryOutcome
import app.punktgenau.data.PlayerRepository

/**
 * Ansicht aus Sicht eines anderen Spielers: dessen öffentliche TTR-Werte und Verlauf (Liste und Diagramm).
 * Es wird nicht als dieser Spieler angemeldet; das Konto bleibt das eigene.
 */
@Composable
fun PlayerViewScreen(
    repo: PlayerRepository,
    clubs: ClubRepository,
    nuId: String,
    name: String,
    club: String?,
    onClose: () -> Unit,
    onUseInCalculator: (Int) -> Unit,
    onOpenClub: (ClubRef) -> Unit,
) {
    var outcome by remember { mutableStateOf<PlayerHistoryOutcome?>(null) }
    var clubRef by remember { mutableStateOf<ClubRef?>(null) }
    LaunchedEffect(nuId) { outcome = repo.history(nuId) }
    LaunchedEffect(club) { clubRef = club?.let { clubs.findClub(it) } }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Sicht von $name")
        club?.let { InfoText(it) }
        clubRef?.let { ref ->
            OutlinedButton({ onOpenClub(ref) }, Modifier.fillMaxWidth()) { Text("Verein ansehen (Mannschaften, Liga)") }
        }
        Spacer(Modifier.height(8.dp))
        InfoText("Öffentliche Werte dieses Spielers. Du bist dabei nicht als er angemeldet.")
        Spacer(Modifier.height(8.dp))
        when (val o = outcome) {
            null -> LoadingBox()
            PlayerHistoryOutcome.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
            is PlayerHistoryOutcome.Failed -> InfoText(o.message)
            is PlayerHistoryOutcome.Ok -> {
                val h = o.history
                val value = h.ttr ?: h.qttr
                AppCard {
                    Text(if (h.ttr != null) "TTR" else "Q-TTR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value?.toString() ?: "–", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    h.qttr?.takeIf { h.ttr != null }?.let { Text("Q-TTR $it", style = MaterialTheme.typography.bodyMedium) }
                    if (value != null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton({ onUseInCalculator(value) }, Modifier.fillMaxWidth()) { Text("Im Rechner als mein Wert nehmen") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Derselbe Verlaufs-Screen wie beim eigenen Konto: Spiele und Diagramm.
                HistoryScreen(events = h.events, loggedIn = true, modifier = Modifier.weight(1f).fillMaxWidth(), showTitle = false)
            }
        }
    }
}
