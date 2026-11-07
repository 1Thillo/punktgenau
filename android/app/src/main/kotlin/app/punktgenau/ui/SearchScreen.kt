package app.punktgenau.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.PlayerHit
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.SearchOutcome
import kotlinx.coroutines.launch

private data class Selected(val player: PlayerHit, val outcome: RatingOutcome?)

@Composable
fun SearchScreen(repo: PlayerRepository, onAddToCalculator: (Int) -> Unit, onNeedsLogin: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<PlayerHit>>(emptyList()) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        scope.launch {
            busy = true; message = null; selected = null
            when (val r = repo.search(query)) {
                is SearchOutcome.Hits -> {
                    hits = r.players
                    if (r.players.isEmpty()) message = "Keine Treffer."
                }
                SearchOutcome.NeedsLogin -> onNeedsLogin()
                is SearchOutcome.Failed -> message = r.message
            }
            busy = false
        }
    }

    fun select(p: PlayerHit) {
        selected = Selected(p, null)
        val id = p.nuId
        if (id == null) {
            message = "Für diesen Spieler fehlt die ID."
            return
        }
        scope.launch {
            when (val r = repo.ratings(id)) {
                RatingOutcome.NeedsLogin -> onNeedsLogin()
                else -> selected = Selected(p, r)
            }
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Suche")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Spieler suchen (Name)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button({ runSearch() }, enabled = !busy && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Suchen") }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        selected?.let { sel ->
            AppCard {
                Text(sel.player.displayName, style = MaterialTheme.typography.titleLarge)
                sel.player.club?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                when (val o = sel.outcome) {
                    null -> CircularProgressIndicator()
                    is RatingOutcome.Failed -> Text(o.message)
                    is RatingOutcome.Values -> {
                        val value = o.ttr ?: o.qttr
                        Text(value?.toString() ?: "–", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                o.ttr != null -> "TTR" + (o.qttr?.let { " · Q-TTR $it" } ?: "")
                                o.qttr != null -> "Q-TTR (TTR wird vom Server nicht geliefert)"
                                else -> "Keine Werte erhalten"
                            },
                        )
                        if (value != null) OutlinedButton({ onAddToCalculator(value) }) { Text("Zum Rechner hinzufügen") }
                    }
                    RatingOutcome.NeedsLogin -> Unit
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(hits) { p ->
                AppCard(onClick = { select(p) }) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                    p.club?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
