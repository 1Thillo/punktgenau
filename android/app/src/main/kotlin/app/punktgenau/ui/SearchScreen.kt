package app.punktgenau.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.AdvancedSearchRepository
import app.punktgenau.data.PlayerHit
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.Result
import app.punktgenau.data.SearchFilter
import app.punktgenau.data.SearchOutcome
import kotlinx.coroutines.launch

private data class Selected(val player: PlayerHit, val outcome: RatingOutcome?)

@Composable
fun SearchScreen(
    repo: PlayerRepository,
    advanced: AdvancedSearchRepository,
    onAddToCalculator: (Int) -> Unit,
    onViewPlayer: (PlayerHit) -> Unit,
    onNeedsLogin: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hits by remember { mutableStateOf<List<PlayerHit>>(emptyList()) }
    var selected by remember { mutableStateOf<Selected?>(null) }
    val scope = rememberCoroutineScope()

    // Erweiterte Suche
    var showAdvanced by remember { mutableStateOf(false) }
    var gender by remember { mutableStateOf("all") }
    var currentTtr by remember { mutableStateOf(true) }
    var ttrMinText by remember { mutableStateOf("") }
    var ttrMaxText by remember { mutableStateOf("") }
    var clubText by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var pages by remember { mutableStateOf(1) }

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

    fun runAdvanced(target: Int) {
        val filter = SearchFilter(
            clubNr = clubText.trim(),
            gender = gender,
            currentTtr = currentTtr,
            ttrMin = ttrMinText.toIntOrNull() ?: 100,
            ttrMax = ttrMaxText.toIntOrNull() ?: 3000,
        )
        scope.launch {
            busy = true; message = null; selected = null
            when (val r = advanced.search(filter, target)) {
                is Result.Ok -> {
                    page = target
                    pages = r.value.pages
                    hits = r.value.players.map { PlayerHit(it.firstName, it.lastName, it.club, it.nuId) }
                    if (hits.isEmpty()) message = "Keine Treffer für diese Filter."
                }
                Result.NeedsLogin -> onNeedsLogin()
                is Result.Failed -> message = r.message
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!showAdvanced, { showAdvanced = false }, { Text("Name") })
            FilterChip(showAdvanced, { showAdvanced = true }, { Text("Erweitert") })
        }

        if (!showAdvanced) {
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
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(gender == "all", { gender = "all" }, { Text("Alle") })
                FilterChip(gender == "m", { gender = "m" }, { Text("Männer") })
                FilterChip(gender == "w", { gender = "w" }, { Text("Frauen") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(currentTtr, { currentTtr = true }, { Text("Aktuell (TTR)") })
                FilterChip(!currentTtr, { currentTtr = false }, { Text("Quartal (Q-TTR)") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ttrMinText,
                    onValueChange = { ttrMinText = it.filter { c -> c.isDigit() } },
                    label = { Text("TTR von") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ttrMaxText,
                    onValueChange = { ttrMaxText = it.filter { c -> c.isDigit() } },
                    label = { Text("TTR bis") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = clubText,
                onValueChange = { clubText = it.filter { c -> c.isDigit() } },
                label = { Text("Vereinsnummer (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button({ runAdvanced(1) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Filtern") }
        }

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
                        if (sel.player.nuId != null) {
                            OutlinedButton({ onViewPlayer(sel.player) }, Modifier.fillMaxWidth()) { Text("Sicht von ${sel.player.firstName}") }
                        }
                    }
                    RatingOutcome.NeedsLogin -> Unit
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(hits) { p ->
                AppCard(onClick = { select(p) }) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium)
                    p.club?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (showAdvanced && hits.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton({ runAdvanced(page - 1) }, enabled = !busy && page > 1) { Text("Zurück") }
                        Text("Seite $page von $pages", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton({ runAdvanced(page + 1) }, enabled = !busy && page < pages) { Text("Weiter") }
                    }
                }
            }
        }
    }
}
