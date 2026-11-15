package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.punktgenau.data.LIVE_INTERVAL_MS
import app.punktgenau.data.MatchTime
import app.punktgenau.data.Result
import app.punktgenau.data.Spieltag
import app.punktgenau.data.SpieltagKalender
import app.punktgenau.data.SpieltagMatch
import app.punktgenau.data.SpieltagRepository
import app.punktgenau.data.TeamRef
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Zustand des Spieltags, der das Öffnen eines Spielberichts oder einer Mannschaft überdauert. */
class SpieltagState {
    var matches by mutableStateOf<List<SpieltagMatch>?>(null)
    var index by mutableIntStateOf(-1)
}

/** Spieltag-Übersicht: die Spiele der eigenen und gemerkten Mannschaften, Tag für Tag. */
@Composable
fun SpieltagScreen(
    repo: SpieltagRepository,
    state: SpieltagState,
    links: Links,
    onClose: () -> Unit,
    onNeedsLogin: () -> Unit,
    onOpenFollow: () -> Unit,
) {
    var failed by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var liveTick by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onClose)

    // Laufende Spiele alle paar Sekunden neu laden. Die Schleife endet mit dem Bildschirm (Coroutine-Abbruch).
    LaunchedEffect(liveTick, reload, state.matches != null) {
        val current = state.matches ?: return@LaunchedEffect
        if (current.none { Spieltag.isRunning(it.appointment, MatchTime.now()) }) return@LaunchedEffect
        delay(LIVE_INTERVAL_MS)
        val r = repo.refreshLive(current)
        if (r is Result.Ok) state.matches = r.value
        liveTick++
    }

    LaunchedEffect(reload) {
        // Beim Zurückkommen (z. B. aus einem Spielbericht) bleibt die geladene Liste stehen.
        if (reload == 0 && state.matches != null) return@LaunchedEffect
        state.matches = null
        failed = null
        when (val r = repo.ownMatches()) {
            is Result.Ok -> {
                state.matches = r.value
                state.index = Spieltag.startIndex(Spieltag.days(r.value), LocalDate.now())
            }
            Result.NeedsLogin -> onNeedsLogin()
            is Result.Failed -> failed = r.message
        }
    }

    val list = state.matches.orEmpty()
    val days = Spieltag.days(list)
    val day = days.getOrNull(state.index)
    val dayMatches = list.filter { it.day == day }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Spieltag")
        InfoText("Spiele deiner Mannschaften und der gemerkten.")
        OutlinedButton(onOpenFollow, Modifier.fillMaxWidth()) { Text("Mannschaften merken") }
        Spacer(Modifier.height(8.dp))
        val f = failed
        val m = state.matches
        if (f != null) {
            InfoText(f)
        } else if (m == null) {
            LoadingBox()
        } else if (days.isEmpty()) {
            InfoText("Keine Spiele gefunden.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ state.index = (state.index - 1).coerceAtLeast(0) }, enabled = state.index > 0) { Text("◀") }
                Text(
                    day?.let { fmtDay(it) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton({ state.index = (state.index + 1).coerceAtMost(days.lastIndex) }, enabled = state.index < days.lastIndex) { Text("▶") }
            }
            OutlinedButton(
                { state.index = Spieltag.startIndex(days, LocalDate.now()) },
                Modifier.fillMaxWidth(),
            ) { Text("Heute / nächster Spieltag") }
            OutlinedButton(
                {
                    val termine = dayMatches.mapNotNull { SpieltagKalender.termin(it) }
                    if (termine.isNotEmpty()) {
                        oeffneKalender(context, kalenderDateiname("spieltag", day.toString()), SpieltagKalender.ics(termine))
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = dayMatches.any { SpieltagKalender.termin(it) != null },
            ) { Text("Spieltag in den Kalender") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dayMatches.isEmpty()) item { InfoText("Keine Spiele an diesem Tag.") }
                items(dayMatches) { match -> MatchCard(match, links) }
                item {
                    OutlinedButton({ reload++ }, Modifier.fillMaxWidth()) { Text("Aktualisieren") }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: SpieltagMatch, links: Links) {
    val a = match.appointment
    val now = MatchTime.now()
    val running = Spieltag.isRunning(a, now)
    val team = match.team
    val own = Spieltag.ownRef(team, a)
    val context = LocalContext.current
    // Der Spielbericht ist erreichbar, sobald die Spielkennung da ist, also auch ohne Ergebnis.
    val report = if (own.group.groupId.isNotBlank()) Spieltag.reportLink(own, a, now) else null
    val home = report?.home ?: if (a.home == false) TeamRef(own.group, a.opponentTeamId, a.opponentName) else own
    val away = report?.away ?: if (a.home == false) own else TeamRef(own.group, a.opponentTeamId, a.opponentName)
    AppCard(onClick = report?.let { r -> { links.report(r) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                team.league.orEmpty() + (match.time?.let { " · $it Uhr" }.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                Text(
                    if (a.live) "LIVE" else "läuft",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(a.result ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        // Mit Spielbericht führt die ganze Karte dorthin (beide Mannschaften stehen dort); sonst öffnet der Name die Mannschaft.
        TeamLine(home.name, bold = home.teamId == team.id, onClick = if (report == null) ({ links.team(home) }) else null)
        TeamLine(away.name, bold = away.teamId == team.id, onClick = if (report == null) ({ links.team(away) }) else null)
        if (report != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (running) "Live-Spielbericht öffnen" else "Spielbericht öffnen",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(
            {
                SpieltagKalender.termin(match, now)?.let { t ->
                    oeffneKalender(
                        context,
                        kalenderDateiname("spiel", t.start.toLocalDate().toString(), t.home, t.away),
                        SpieltagKalender.ics(listOf(t)),
                    )
                }
            },
            enabled = SpieltagKalender.termin(match, now) != null,
        ) { Text("In den Kalender") }
    }
}

@Composable
private fun TeamLine(name: String, bold: Boolean, onClick: (() -> Unit)?) {
    Text(
        name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

private fun fmtDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN))
