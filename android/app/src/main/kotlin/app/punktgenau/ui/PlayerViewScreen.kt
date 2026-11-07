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
import app.punktgenau.data.HistoryEvent
import app.punktgenau.data.PlayerHistoryOutcome
import app.punktgenau.data.PlayerRepository

/**
 * Ansicht aus Sicht eines anderen Spielers: dessen öffentliche TTR-Werte und Verlauf.
 * Es wird nicht als dieser Spieler angemeldet; das Konto bleibt das eigene.
 */
@Composable
fun PlayerViewScreen(repo: PlayerRepository, nuId: String, name: String, club: String?, onClose: () -> Unit) {
    var outcome by remember { mutableStateOf<PlayerHistoryOutcome?>(null) }
    LaunchedEffect(nuId) { outcome = repo.history(nuId) }
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Sicht von $name")
        club?.let { InfoText(it) }
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
                }
                Spacer(Modifier.height(10.dp))
                SectionLabel("Verlauf")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(h.events.reversed()) { e: HistoryEvent -> HistoryRow(e) }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEvent) {
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(e.name.ifBlank { e.type }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
            Text("${e.after}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Text(
            "${e.date}  ·  ${e.before} → ${e.after}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
