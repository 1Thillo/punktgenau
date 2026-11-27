package app.punktgenau.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Tournament
import app.punktgenau.data.TournamentOutcome
import app.punktgenau.data.TournamentRepository
import app.punktgenau.data.Verbaende
import java.time.LocalDate

/** Lesbare Bezeichnung eines Turnier-Typs aus dem Kalender. */
internal fun tournamentTypeLabel(type: String): String = when (type) {
    "open" -> "Offen"
    "qualification" -> "Qualifikation"
    "circuit" -> "Turnierserie"
    else -> type
}

/**
 * Turnierkalender: kommende Turniere aller Verbände, nach Typ und Suchbegriff gefiltert. Abgesagte und
 * vergangene Turniere fallen weg.
 */
@Composable
fun TournamentScreen(repo: TournamentRepository, ownAssociation: suspend () -> String?, onClose: () -> Unit) {
    var state by remember { mutableStateOf<TournamentOutcome?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var own by remember { mutableStateOf<String?>(null) }
    var onlyOwn by remember { mutableStateOf(true) }
    var typed by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        // Angemeldet: standardmäßig nur Turniere des eigenen Verbands.
        own = ownAssociation()
        state = repo.load()
    }

    val today = LocalDate.now().toString()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackBar(onClose)
        ScreenTitle("Turniere")
        own?.let { code ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(onlyOwn, { onlyOwn = true }, { Text("Mein Verband (${Verbaende.name(code)})", maxLines = 1, softWrap = false) })
                FilterChip(!onlyOwn, { onlyOwn = false }, { Text("Alle Verbände", maxLines = 1, softWrap = false) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(typeFilter == null, { typeFilter = null }, { Text("Alle", maxLines = 1, softWrap = false) })
            listOf("open", "qualification", "circuit").forEach { t ->
                FilterChip(typeFilter == t, { typeFilter = t }, { Text(tournamentTypeLabel(t), maxLines = 1, softWrap = false) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Name oder Ort") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Button({ applied = typed }, Modifier.padding(top = 6.dp)) { Text("Suchen") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                null -> item { LoadingBox() }
                is TournamentOutcome.Failed -> item { InfoText(s.message) }
                is TournamentOutcome.Ok -> {
                    val shown = s.tournaments
                        .filter { it.startDay >= today && it.state != "cancelled" }
                        .filter { typeFilter == null || it.type == typeFilter }
                        .filter { !onlyOwn || own == null || it.organization == own }
                        .filter { applied.isBlank() || it.name.contains(applied.trim(), true) || it.location.contains(applied.trim(), true) }
                    item { SectionLabel("${shown.size} Turniere") }
                    items(shown) { t -> TournamentCard(t) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TournamentCard(t: Tournament) {
    // Tipp auf die Karte öffnet die Turnierseite der Website im Browser (wie beim Spieltag).
    val context = LocalContext.current
    val url = "https://www.mytischtennis.de/turnierwelt/turniere/${t.id}"
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBadge(t.startText.take(5), t.startText.takeLast(4))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(t.host.takeIf { it.isNotBlank() }, t.location.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(tournamentTypeLabel(t.type))
                    if (t.organization.isNotBlank()) Pill(t.organization)
                    if (t.startTime.isNotBlank()) Pill("${t.startTime} Uhr")
                    if (t.endText.isNotBlank() && t.endText != t.startText) Pill("bis ${t.endText.take(5)}")
                }
            }
        }
    }
}
