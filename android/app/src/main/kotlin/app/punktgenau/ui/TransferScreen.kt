package app.punktgenau.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import app.punktgenau.data.RttvrRegionen
import app.punktgenau.data.Verbaende
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun TransferScreen(repo: TransferRepository, ownAssociation: suspend () -> String?, onClose: () -> Unit) {
    var association by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf<String?>(null) }
    var regionMenuOpen by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf(true) }
    var period by remember { mutableStateOf<TransferPeriod?>(null) }
    var typed by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<Result<TransferPage>?>(null) }

    // Standard ist der Verband des eigenen Kontos; ohne bekannten Verband RTTVR.
    LaunchedEffect(Unit) { association = ownAssociation() ?: Verbaende.STANDARD }
    // Der Server filtert nach Name nicht; die Namenssuche passiert unten im Browser, wie auf der Website.
    LaunchedEffect(association, incoming, period, region) {
        val a = association ?: return@LaunchedEffect
        state = null
        state = repo.load(a, incoming, period?.from, "", region)
    }
    BackHandler(onBack = onClose)

    val page = (state as? Result.Ok)?.value
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackBar(onClose)
        ScreenTitle("Wechselliste")
        Box {
            OutlinedButton({ menuOpen = true }) { Text("Verband: ${Verbaende.name(association)} ▾") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Verbaende.alle.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            // Zeiträume und Regionen gelten je Verband, deshalb beides zurücksetzen.
                            association = code
                            period = null
                            region = null
                            menuOpen = false
                        },
                    )
                }
            }
        }
        // Regionscodes sind nur für den RTTVR bekannt; bei anderen Verbänden gibt es keinen Regionsfilter.
        if (association == "RTTVR") {
            Box {
                OutlinedButton({ regionMenuOpen = true }) { Text("Region: ${RttvrRegionen.name(region)} ▾") }
                DropdownMenu(expanded = regionMenuOpen, onDismissRequest = { regionMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Alle Regionen") },
                        onClick = { region = null; regionMenuOpen = false },
                    )
                    RttvrRegionen.alle.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { region = code; regionMenuOpen = false },
                        )
                    }
                }
            }
        }
        Segmented(listOf("Eingehend", "Ausgehend"), if (incoming) 0 else 1) { incoming = it == 0 }
        page?.periods?.takeIf { it.isNotEmpty() }?.let { periods ->
            // Einzeilig und horizontal scrollbar: umbrechende Chips haben die Kopfzeile aufgebläht.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(period == null, { period = null }, { Text("Standard", maxLines = 1, softWrap = false) })
                periods.forEach { p ->
                    FilterChip(period == p, { period = p }, { Text(p.label, maxLines = 1, softWrap = false) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button({ applied = typed }, Modifier.padding(top = 6.dp)) { Text("Suchen") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val s = state) {
                null -> item { LoadingBox() }
                is Result.Ok -> {
                    val shown = s.value.transfers.filter { applied.isBlank() || it.name.contains(applied.trim(), ignoreCase = true) }
                    item { SectionLabel("${shown.size} Wechsel") }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LetterBadge(t.name)
            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        // Von und Nach untereinander, damit lange Vereinsnamen lesbar bleiben.
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ClubLine("Von", t.oldClub.ifBlank { "–" }, MaterialTheme.colorScheme.onSurfaceVariant)
            ClubLine("Nach", t.newClub.ifBlank { "–" }, MaterialTheme.colorScheme.primary)
        }
        if (t.parts.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.parts.forEach { Pill(partLabel(it)) }
            }
        }
    }
}

@Composable
private fun ClubLine(label: String, club: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        Text(club, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Lizenzarten aus der Wechselliste; unbekannte Kürzel bleiben unverändert. */
private fun partLabel(code: String): String = when (code) {
    "adultsTeamMatches" -> "Erwachsene Mannschaft"
    "seniorsTeamMatches" -> "Senioren Mannschaft"
    else -> code
}
