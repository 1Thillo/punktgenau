package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import app.punktgenau.data.HistoryEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.ClubRepository
import app.punktgenau.data.HeadToHeadResult
import app.punktgenau.data.MatchFinder
import app.punktgenau.data.PlayerHistoryOutcome
import app.punktgenau.data.PlayerHit
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.SearchOutcome
import app.punktgenau.data.SpielberichtRepository
import app.punktgenau.data.loadHeadToHead
import app.punktgenau.domain.Statistics
import app.punktgenau.domain.StatsSummary
import kotlinx.coroutines.launch

/**
 * Direktvergleich: der geöffnete Spieler und ein zweiter, den man sucht. Nebeneinander stehen TTR, Verlauf und
 * Siegquote; die Bilanz gegeneinander lädt man auf Wunsch, weil dafür Spielberichte geholt werden.
 */
@Composable
fun CompareScreen(
    first: PlayerLink,
    repo: PlayerRepository,
    finder: MatchFinder,
    clubs: ClubRepository,
    spielbericht: SpielberichtRepository,
    onClose: () -> Unit,
) {
    var second by remember { mutableStateOf<PlayerLink?>(null) }
    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackBar(onClose) {
            if (second != null) AccentAction("Anderer Spieler ›", { second = null })
        }
        ScreenTitle("Direktvergleich")
        val b = second
        if (b == null) {
            PickSecond(first, repo) { second = it }
        } else {
            Comparison(first, b, repo, finder, clubs, spielbericht)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PickSecond(first: PlayerLink, repo: PlayerRepository, onPick: (PlayerLink) -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PlayerHit>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    InfoText("Wähle den zweiten Spieler. ${first.name} ist schon gesetzt.")
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    AccentAction("Suchen", {
        scope.launch {
            busy = true
            message = null
            when (val r = repo.search(query)) {
                is SearchOutcome.Hits -> {
                    // Nur Treffer mit Kennung und nicht der Spieler selbst.
                    hits = r.players.filter { it.nuId != null && it.nuId != first.nuId }
                    if (hits.isEmpty()) message = "Keine Treffer."
                }
                SearchOutcome.NeedsLogin -> message = "Bitte zuerst anmelden."
                is SearchOutcome.Failed -> message = r.message
            }
            busy = false
        }
    })
    if (busy) InfoText("Suche …")
    message?.let { InfoText(it) }
    hits.forEach { p ->
        AppCard(onClick = { onPick(PlayerLink(p.nuId.orEmpty(), p.displayName, club = p.club)) }) {
            Text(p.displayName, style = MaterialTheme.typography.titleMedium)
            p.club?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Comparison(
    first: PlayerLink,
    second: PlayerLink,
    repo: PlayerRepository,
    finder: MatchFinder,
    clubs: ClubRepository,
    spielbericht: SpielberichtRepository,
) {
    val h1 by produceState<PlayerHistoryOutcome?>(null, first.nuId) { value = repo.history(first.nuId) }
    val h2 by produceState<PlayerHistoryOutcome?>(null, second.nuId) { value = repo.history(second.nuId) }
    val a = (h1 as? PlayerHistoryOutcome.Ok)?.history
    val b = (h2 as? PlayerHistoryOutcome.Ok)?.history
    if (a == null || b == null) {
        val note = outcomeNote(h1, first.name) ?: outcomeNote(h2, second.name)
        if (note != null) InfoText(note) else LoadingBox()
        return
    }

    val sa = Statistics.summarize(a.events)
    val sb = Statistics.summarize(b.events)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1.1f))
            Text(first.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
            Text(second.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
        CompareRow("TTR", a.ttr?.toString() ?: "–", b.ttr?.toString() ?: "–")
        CompareRow("Q-TTR", a.qttr?.toString() ?: "–", b.qttr?.toString() ?: "–")
        CompareRow("Siegquote Einzel", winRate(sa), winRate(sb))
        CompareRow("Mannschaftsspiele", sa.meetings.toString(), sb.meetings.toString())
        CompareRow("Siege : Niederlagen", "${sa.meetingWins} : ${sa.meetingLosses}", "${sb.meetingWins} : ${sb.meetingLosses}")
        CompareRow("Größter Zuwachs", sa.biggestGain?.let { signed(it.delta) } ?: "–", sb.biggestGain?.let { signed(it.delta) } ?: "–")
        CompareRow("Größter Verlust", sa.biggestLoss?.let { signed(it.delta) } ?: "–", sb.biggestLoss?.let { signed(it.delta) } ?: "–")
        Text("Verlauf gesamt, Werte öffentlich.", style = MaterialTheme.typography.bodySmall, color = muted)
    }

    OverlayChartCard(first.name, a.events, second.name, b.events)

    HeadToHeadCard(first, second, a.events, finder, clubs, spielbericht)
}

/** Beide TTR-Verläufe in einem Diagramm übereinander, gleiche Zeit- und Wertachse. */
@Composable
private fun OverlayChartCard(aName: String, aEvents: List<HistoryEvent>, bName: String, bEvents: List<HistoryEvent>) {
    val all = aEvents + bEvents
    if (all.size < 2) return
    val colorA = MaterialTheme.colorScheme.primary
    val colorB = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val minDay = all.minOf { it.date.toEpochDay() }
    val maxDay = all.maxOf { it.date.toEpochDay() }
    val span = (maxDay - minDay).coerceAtLeast(1).toFloat()
    val minV = all.minOf { it.after }
    val maxV = all.maxOf { it.after }
    val pad = (maxV - minV).coerceAtLeast(10) * 0.12f
    val lo = minV - pad
    val hi = maxV + pad

    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("● $aName", style = MaterialTheme.typography.bodySmall, color = colorA)
            Text("● $bName", style = MaterialTheme.typography.bodySmall, color = colorB)
        }
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            fun x(e: HistoryEvent) = (e.date.toEpochDay() - minDay) / span * size.width
            fun y(v: Int) = size.height - (v - lo) / (hi - lo) * size.height
            fun pathOf(events: List<HistoryEvent>) = Path().apply {
                events.forEachIndexed { i, e ->
                    if (i == 0) moveTo(x(e), y(e.after)) else lineTo(x(e), y(e.after))
                }
            }
            // Drei waagerechte Hilfslinien für die Orientierung.
            for (i in 0..2) {
                val gy = size.height * i / 2f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            drawPath(pathOf(aEvents), colorA, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawPath(pathOf(bEvents), colorB, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        Text(
            "Werte von $minV bis $maxV",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun HeadToHeadCard(
    first: PlayerLink,
    second: PlayerLink,
    aEvents: List<app.punktgenau.data.HistoryEvent>,
    finder: MatchFinder,
    clubs: ClubRepository,
    spielbericht: SpielberichtRepository,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var progress by remember(first.nuId, second.nuId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember(first.nuId, second.nuId) { mutableStateOf<HeadToHeadResult?>(null) }
    val scope = rememberCoroutineScope()

    AppCard {
        Text("Bilanz gegeneinander", style = MaterialTheme.typography.bodySmall, color = muted)
        val r = result
        val p = progress
        when {
            r != null -> {
                if (r.total.lines == 0) {
                    Text("Keine gemeinsame Begegnung in dieser Saison gefunden.", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(
                        "${r.total.aWins} : ${r.total.bWins}",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${first.name} gegen ${second.name}: ${r.total.lines} Begegnungen in dieser Saison",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
                Text(
                    "${r.reportsChecked} Spielberichte geprüft" + if (r.reportsFailed > 0) ", ${r.reportsFailed} nicht ladbar" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            p != null -> InfoText("Lade Spielberichte … ${p.first} von ${p.second}")
            else -> AccentAction("Bilanz laden", {
                scope.launch {
                    progress = 0 to 0
                    // Vereinsnummer wie auf der Spielerseite: aus dem Verweis, sonst über den Vereinsnamen.
                    val known = first.clubNr ?: first.team?.clubNr
                    val club = if (known == null) first.club?.let { clubs.findClub(it) } else null
                    val nr = known ?: club?.clubNr
                    val assoc = first.team?.group?.association ?: club?.association ?: "RTTVR"
                    result = loadHeadToHead(
                        aId = first.nuId,
                        bId = second.nuId,
                        aEvents = aEvents,
                        findReport = { e -> findPlayerReport(finder, clubs, first, nr, assoc, e) },
                        spielbericht = spielbericht,
                        onProgress = { done, total -> progress = done to total },
                    )
                    progress = null
                }
            })
        }
    }
}

@Composable
private fun CompareRow(label: String, left: String, right: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(right, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun winRate(s: StatsSummary): String = s.matchWinRate?.let { "$it %" } ?: "–"

private fun signed(delta: Int): String = if (delta > 0) "+$delta" else "−${-delta}"

private fun outcomeNote(o: PlayerHistoryOutcome?, name: String): String? = when (o) {
    null -> null
    PlayerHistoryOutcome.NeedsLogin -> "Bitte zuerst anmelden."
    is PlayerHistoryOutcome.Failed -> "$name: ${o.message}"
    is PlayerHistoryOutcome.Ok -> null
}
