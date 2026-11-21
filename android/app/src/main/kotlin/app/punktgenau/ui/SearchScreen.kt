package app.punktgenau.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
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
import app.punktgenau.data.SearchHistory
import app.punktgenau.data.SearchOutcome
import kotlinx.coroutines.launch

private data class Selected(val player: PlayerHit, val outcome: RatingOutcome?)

@Composable
fun SearchScreen(
    repo: PlayerRepository,
    advanced: AdvancedSearchRepository,
    history: SearchHistory,
    onAddToCalculator: (Int) -> Unit,
    onViewPlayer: (PlayerHit) -> Unit,
    onNeedsLogin: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(history.load()) }
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
                    history.add(query)
                    recent = history.load()
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

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Suche") }
        item { Segmented(listOf("Name", "Erweitert"), if (showAdvanced) 1 else 0) { showAdvanced = it == 1 } }

        if (!showAdvanced) {
            item {
                AppCard {
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
                }
            }
            if (recent.isNotEmpty()) {
                item {
                    // Letzte Suchen zum Antippen; „Verlauf löschen“ leert die Liste auf diesem Gerät.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f).padding(bottom = 10.dp)) { SectionLabel("Zuletzt gesucht") }
                        TextButton(onClick = { history.clear(); recent = emptyList() }) { Text("Verlauf löschen") }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        recent.forEach { term ->
                            AssistChip(onClick = { query = term; runSearch() }, label = { Text(term) })
                        }
                    }
                }
            }
        } else {
            item {
                AppCard {
                    Text("Geschlecht", style = MaterialTheme.typography.labelLarge)
                    val genders = listOf("all", "m", "w")
                    Segmented(listOf("Alle", "Männer", "Frauen"), genders.indexOf(gender)) { gender = genders[it] }
                    Spacer(Modifier.height(6.dp))
                    Text("Wert", style = MaterialTheme.typography.labelLarge)
                    Segmented(listOf("Aktuell (TTR)", "Quartal (Q-TTR)"), if (currentTtr) 0 else 1) { currentTtr = it == 0 }
                    Spacer(Modifier.height(6.dp))
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
            }
        }

        if (busy) item { LoadingBox() }
        message?.let { m -> item { InfoText(m) } }

        if (hits.isNotEmpty()) item { SectionLabel(if (hits.size == 1) "1 Treffer" else "${hits.size} Treffer") }
        items(hits) { p ->
            ListCard(p.displayName, p.club, onClick = { select(p) }, leading = { LetterBadge(p.displayName) })
            // Die gewählte Person klappt direkt unter ihrer Zeile als Hero-Fläche auf.
            val sel = selected
            if (sel != null && sel.player == p) {
                Spacer(Modifier.height(10.dp))
                HeroCard {
                    Text("AUSGEWÄHLT", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    Text(sel.player.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    sel.player.club?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)) }
                    when (val o = sel.outcome) {
                        null -> CircularProgressIndicator(color = Color.White)
                        is RatingOutcome.Failed -> Text(o.message, color = Color.White)
                        is RatingOutcome.Values -> {
                            val value = o.ttr ?: o.qttr
                            Text(value?.toString() ?: "–", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                when {
                                    o.ttr != null -> "TTR" + (o.qttr?.let { " · Q-TTR $it" } ?: "")
                                    o.qttr != null -> "Q-TTR (TTR wird vom Server nicht geliefert)"
                                    else -> "Keine Werte erhalten"
                                },
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (value != null) HeroChoice("Zum Rechner hinzufügen", { onAddToCalculator(value) }, Modifier.weight(1f))
                                if (sel.player.nuId != null) HeroChoice("Sicht von ${sel.player.firstName}", { onViewPlayer(sel.player) }, Modifier.weight(1f))
                            }
                        }
                        RatingOutcome.NeedsLogin -> Unit
                    }
                }
            }
        }
        if (showAdvanced && hits.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    AccentAction("‹ Zurück", { runAdvanced(page - 1) }, enabled = !busy && page > 1)
                    Text("Seite $page von $pages", style = MaterialTheme.typography.bodyMedium)
                    AccentAction("Weiter ›", { runAdvanced(page + 1) }, enabled = !busy && page < pages)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
