package app.punktgenau.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.domain.AgeGroup
import app.punktgenau.domain.AlterationInput
import app.punktgenau.domain.Game
import app.punktgenau.domain.TtrCalculator

class GameRow(opponent: String = "", won: Boolean = false) {
    var opponent by mutableStateOf(opponent)
    var won by mutableStateOf(won)
}

/** Zustand des Rechners; wird von mehreren Screens genutzt (Suche fügt Gegner hinzu). */
class CalculatorState {
    var ownText by mutableStateOf("")
    var age by mutableStateOf(AgeGroup.ADULT)
    var fewSingles by mutableStateOf(false)
    var breakFlag by mutableStateOf(false)
    val rows = mutableStateListOf(GameRow())

    /** Füllt zuerst eine leere Zeile, sonst kommt eine neue dazu. */
    fun addOpponent(rating: Int) {
        val empty = rows.firstOrNull { it.opponent.isBlank() }
        if (empty != null) empty.opponent = rating.toString() else rows.add(GameRow(rating.toString()))
    }
}

@Composable
fun CalculatorScreen(state: CalculatorState, onLoadAppointment: () -> Unit = {}, onResetOwn: () -> Unit = {}) {
    var ownText by state::ownText
    var age by state::age
    var fewSingles by state::fewSingles
    var breakFlag by state::breakFlag
    val rows = state.rows

    val own = ownText.toIntOrNull()
    val games = rows.mapNotNull { r -> r.opponent.toIntOrNull()?.let { Game(it, r.won) } }
    val result = own?.let { TtrCalculator.calculate(it, games, AlterationInput(age, fewSingles, breakFlag)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Rechner") }
        item {
            AppCard {
                val change = if (games.isEmpty()) null else result?.change
                val (text, color) = when {
                    change == null -> "–" to MaterialTheme.colorScheme.onSurface
                    else -> deltaText(change)
                }
                Text(
                    text,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.semantics {
                        contentDescription = when {
                            change == null -> "Noch keine Berechnung"
                            change > 0 -> "Gewinn von $change Punkten"
                            change < 0 -> "Verlust von ${-change} Punkten"
                            else -> "Keine Änderung"
                        }
                    },
                )
                if (result != null && games.isNotEmpty()) {
                    Text("${result.ownRating} → ${result.newRating}", style = MaterialTheme.typography.titleLarge)
                    Text("ÄK ${result.alterationConstant}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = ownText,
                onValueChange = { ownText = it.filter(Char::isDigit).take(4) },
                label = { Text("Eigener TTR") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onResetOwn, Modifier.fillMaxWidth()) { Text("Mein TTR neu setzen") }
        }
        item {
            AppCard {
                Text("Änderungskonstante", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(age == AgeGroup.UNDER_21, { age = if (age == AgeGroup.UNDER_21) AgeGroup.ADULT else AgeGroup.UNDER_21 }, { Text("unter 21") })
                    FilterChip(age == AgeGroup.UNDER_16, { age = if (age == AgeGroup.UNDER_16) AgeGroup.ADULT else AgeGroup.UNDER_16 }, { Text("unter 16") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("weniger als 30 Einzel", Modifier.weight(1f))
                    Switch(fewSingles, { fewSingles = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1 Jahr Pause, danach < 15 Einzel", Modifier.weight(1f))
                    Switch(breakFlag, { breakFlag = it })
                }
            }
        }
        item { SectionLabel("Spiele") }
        itemsIndexed(rows) { index, row ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = row.opponent,
                        onValueChange = { row.opponent = it.filter(Char::isDigit).take(4) },
                        label = { Text("Gegner ${index + 1}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = row.won,
                        onClick = { row.won = !row.won },
                        label = { Text(if (row.won) "Sieg" else "Niederlage") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PositiveGreen,
                            selectedLabelColor = Color.White,
                            containerColor = NegativeRed,
                            labelColor = Color.White,
                        ),
                        border = null,
                    )
                    TextButton({ rows.removeAt(index) }) { Text("✕") }
                }
            }
        }
        item { Button({ rows.add(GameRow()) }, Modifier.fillMaxWidth()) { Text("Spiel hinzufügen") } }
        item { OutlinedButton(onLoadAppointment, Modifier.fillMaxWidth()) { Text("Aus Termin laden") } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
