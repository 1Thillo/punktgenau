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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Result
import app.punktgenau.data.Transfer
import app.punktgenau.data.TransferPage
import app.punktgenau.data.TransferPeriod
import app.punktgenau.data.TransferRepository

/** Wechselliste des Verbands: eingehende oder ausgehende Lizenzwechsel, nach Zeitraum und Name gefiltert. */
@Composable
fun TransferScreen(repo: TransferRepository, association: String, onClose: () -> Unit) {
    var incoming by remember { mutableStateOf(true) }
    var period by remember { mutableStateOf<TransferPeriod?>(null) }
    var typed by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<Result<TransferPage>?>(null) }

    // Der Server filtert nach Name nicht; die Namenssuche passiert unten im Browser, wie auf der Website.
    LaunchedEffect(incoming, period) {
        state = null
        state = repo.load(association, incoming, period?.from, "")
    }
    BackHandler(onBack = onClose)

    val page = (state as? Result.Ok)?.value
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Wechselliste")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(incoming, { incoming = true }, { Text("Eingehend") })
            FilterChip(!incoming, { incoming = false }, { Text("Ausgehend") })
        }
        page?.periods?.takeIf { it.isNotEmpty() }?.let { periods ->
            Spacer(Modifier.height(8.dp))
            // Einzeilig und horizontal scrollbar: umbrechende Chips haben die Kopfzeile aufgebläht.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(period == null, { period = null }, { Text("Standard", maxLines = 1, softWrap = false) })
                periods.forEach { p ->
                    FilterChip(period == p, { period = p }, { Text(p.label, maxLines = 1, softWrap = false) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Button({ applied = typed }, Modifier.fillMaxWidth()) { Text("Suchen") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                null -> item { LoadingBox() }
                is Result.Ok -> {
                    val shown = s.value.transfers.filter { applied.isBlank() || it.name.contains(applied.trim(), ignoreCase = true) }
                    item { InfoText("${shown.size} Wechsel") }
                    items(shown) { t: Transfer -> TransferCard(t) }
                }
                Result.NeedsLogin -> item { InfoText("Bitte zuerst anmelden.") }
                is Result.Failed -> item { InfoText(s.message) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransferCard(t: Transfer) {
    AppCard {
        Text(t.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "${t.oldClub.ifBlank { "–" }} → ${t.newClub.ifBlank { "–" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.parts.isNotEmpty()) {
            Text(t.parts.joinToString(" · ") { partLabel(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Lizenzarten aus der Wechselliste; unbekannte Kürzel bleiben unverändert. */
private fun partLabel(code: String): String = when (code) {
    "adultsTeamMatches" -> "Erwachsene Mannschaft"
    "seniorsTeamMatches" -> "Senioren Mannschaft"
    else -> code
}
