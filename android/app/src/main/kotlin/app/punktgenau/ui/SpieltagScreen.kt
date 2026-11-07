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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.punktgenau.data.Result
import app.punktgenau.data.Spieltag
import app.punktgenau.data.SpieltagMatch
import app.punktgenau.data.Season
import app.punktgenau.data.SpieltagRepository
import app.punktgenau.data.TeamAverage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Spieltag-Übersicht: die Spiele der eigenen Mannschaften, Tag für Tag, mit der Ø-TTR beider Mannschaften.
 * Die Ø-TTR kommt aus der Meldeliste der Mannschaft; sie ist eine Näherung, keine Aufstellung.
 */
@Composable
fun SpieltagScreen(
    repo: SpieltagRepository,
    onClose: () -> Unit,
    onNeedsLogin: () -> Unit,
    onOpenFollow: () -> Unit,
    onOpenReport: (String, String) -> Unit,
) {
    var matches by remember { mutableStateOf<List<SpieltagMatch>?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(-1) }
    val averages = remember { mutableStateMapOf<String, TeamAverage?>() }
    BackHandler(onBack = onClose)

    LaunchedEffect(reload) {
        matches = null
        failed = null
        when (val r = repo.ownMatches()) {
            is Result.Ok -> {
                matches = r.value
                index = Spieltag.startIndex(Spieltag.days(r.value), LocalDate.now())
            }
            Result.NeedsLogin -> onNeedsLogin()
            is Result.Failed -> failed = r.message
        }
    }

    val list = matches.orEmpty()
    val days = Spieltag.days(list)
    val day = days.getOrNull(index)
    val dayMatches = list.filter { it.day == day }
    val teamIds = dayMatches.flatMap { listOf(it.team.id, it.appointment.opponentTeamId) }.distinct()

    // Ø-TTR nur für Mannschaften laden, die an diesem Tag spielen und noch nicht bekannt sind.
    LaunchedEffect(teamIds) {
        for (id in teamIds) {
            if (averages.containsKey(id)) continue
            averages[id] = (repo.teamAverage(id) as? Result.Ok)?.value
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Spieltag")
        InfoText("Spiele deiner Mannschaften und der gemerkten. Ø-TTR: die vier höchsten Werte aus der Meldeliste (TTR, sonst Q-TTR).")
        OutlinedButton(onOpenFollow, Modifier.fillMaxWidth()) { Text("Mannschaften merken") }
        Spacer(Modifier.height(8.dp))
        val f = failed
        val m = matches
        if (f != null) {
            InfoText(f)
        } else if (m == null) {
            LoadingBox()
        } else if (days.isEmpty()) {
            InfoText("Keine Spiele gefunden.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ index = (index - 1).coerceAtLeast(0) }, enabled = index > 0) { Text("◀") }
                Text(
                    day?.let { fmtDay(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ index = (index + 1).coerceAtMost(days.lastIndex) }, enabled = index < days.lastIndex) { Text("▶") }
            }
            OutlinedButton(
                { index = Spieltag.startIndex(days, LocalDate.now()) },
                Modifier.fillMaxWidth(),
            ) { Text("Heute / nächster Spieltag") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dayMatches.isEmpty()) item { InfoText("Keine Spiele an diesem Tag.") }
                items(dayMatches) { match -> MatchCard(match, averages, onOpenReport) }
                item {
                    OutlinedButton({ reload++ }, Modifier.fillMaxWidth()) { Text("Aktualisieren") }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: SpieltagMatch, averages: Map<String, TeamAverage?>, onOpenReport: (String, String) -> Unit) {
    val report = match.appointment.let { a ->
        val meeting = a.meetingId
        val league = a.leagueId
        if (a.result != null && meeting != null && league != null) {
            Spieltag.reportUrl(match.team.organization ?: "RTTVR", match.team.season ?: Season.label(LocalDate.now()), league, meeting)
        } else {
            null
        }
    }
    val home = match.appointment.home == true
    val ownId = match.team.id
    val oppId = match.appointment.opponentTeamId
    val homeId = if (home) ownId else oppId
    val awayId = if (home) oppId else ownId
    val ownLabel = match.team.club?.let { "$it – ${match.team.name}" } ?: match.team.name
    val homeName = if (home) ownLabel else match.appointment.opponentName
    val awayName = if (home) match.appointment.opponentName else ownLabel
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                match.team.league.orEmpty() + (match.time?.let { " · $it Uhr" }.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(match.appointment.result ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        TeamLine(homeName, averageText(averages, homeId), bold = homeId == ownId)
        TeamLine(awayName, averageText(averages, awayId), bold = awayId == ownId)
        if (report != null) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                { onOpenReport(report, "$homeName gegen $awayName") },
                Modifier.fillMaxWidth(),
            ) { Text("Spielbericht öffnen") }
        }
    }
}

@Composable
private fun TeamLine(name: String, average: String, bold: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(average, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun averageText(averages: Map<String, TeamAverage?>, id: String): String {
    if (!averages.containsKey(id)) return "Ø lädt …"
    val a = averages[id] ?: return "Ø –"
    return "Ø ${a.value}" + (if (a.isTtr) "" else " (Q-TTR)") + " aus ${a.count}"
}

private fun fmtDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN))
