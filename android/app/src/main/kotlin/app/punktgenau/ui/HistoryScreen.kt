package app.punktgenau.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.HistoryEvent
import app.punktgenau.data.ReportLink
import app.punktgenau.domain.Statistics
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** TTR-Verlauf: Liste der Ereignisse (neueste zuerst) oder Diagramm. */
@Composable
fun HistoryScreen(
    events: List<HistoryEvent>,
    loggedIn: Boolean,
    modifier: Modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
    showTitle: Boolean = true,
    /** Sucht den Spielbericht zu einem Ereignis; null, wenn es dafür keine Verknüpfung gibt. */
    findReport: (suspend (HistoryEvent) -> ReportLink?)? = null,
    onOpenReport: (ReportLink) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // Ereignis, dessen Spiel gerade gesucht wird, und Ereignisse ohne Treffer (Schlüssel: Zeitpunkt und Name).
    var searching by remember { mutableStateOf<String?>(null) }
    var notFound by remember { mutableStateOf(setOf<String>()) }
    var view by remember { mutableIntStateOf(0) } // 0 = Spiele, 1 = Diagramm
    var range by remember { mutableIntStateOf(0) } // 0 = Saison, 1 = 1 Jahr, 2 = Alles
    val today = LocalDate.now()
    val seasonStart = LocalDate.of(if (today.monthValue >= 7) today.year else today.year - 1, 7, 1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) ScreenTitle("TTR-Verlauf")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == 0, { view = 0 }, { Text("Spiele") })
            FilterChip(view == 1, { view = 1 }, { Text("Diagramm") })
            FilterChip(view == 2, { view = 2 }, { Text("Statistik") })
        }
        if (events.isEmpty()) {
            InfoText(if (loggedIn) "Noch keine Ereignisse geladen. Auf dem Start-Reiter aktualisieren." else "Bitte zuerst anmelden.")
            return@Column
        }
        if (view >= 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(range == 0, { range = 0 }, { Text("Saison") })
                FilterChip(range == 1, { range = 1 }, { Text("1 Jahr") })
                FilterChip(range == 2, { range = 2 }, { Text("Alles") })
            }
            val from = when (range) { 0 -> seasonStart; 1 -> today.minusYears(1); else -> LocalDate.MIN }
            val shown = events.filter { it.date >= from }
            if (view == 1) ChartCard(shown) else StatsCard(shown)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events.asReversed()) { e ->
                    val key = e.dateTime + e.name
                    val open: (() -> Unit)? = if (findReport != null && e.type == "meeting") ({
                        if (searching == null) {
                            searching = key
                            scope.launch {
                                val link = findReport(e)
                                searching = null
                                if (link != null) onOpenReport(link) else notFound = notFound + key
                            }
                        }
                    }) else null
                    EventCard(
                        e,
                        onClick = open,
                        note = when {
                            searching == key -> "Suche das Spiel im Spielplan …"
                            key in notFound -> "Kein Spielbericht gefunden. Verknüpfbar sind nur Spiele der laufenden Saison."
                            else -> null
                        },
                    )
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EventCard(e: HistoryEvent, onClick: (() -> Unit)? = null, note: String? = null) {
    val (league, teams) = splitName(e)
    val (delta, color) = deltaText(e.delta)
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DateBadge("%02d.%02d.".format(e.date.dayOfMonth, e.date.monthValue), e.date.year.toString())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (league != null) Text(league, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(teams, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.ak?.let { Pill("ÄK $it") }
                    if (e.type == "meeting") Pill("${e.won}:${e.lost}")
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(e.after.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(delta, color = color, fontWeight = FontWeight.SemiBold)
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Liga und Paarung aus "Liga | Heim : Gast"; sonstige Ereignisse behalten ihren Namen. */
internal fun splitName(e: HistoryEvent): Pair<String?, String> {
    val parts = e.name.split(" | ", limit = 2)
    return if (e.type == "meeting" && parts.size == 2) parts[0] to parts[1]
    else (if (e.type == "correction") "Korrektur" else null) to e.name.replace("Spielstaerkezuwachs", "Spielstärkezuwachs")
}

/** Rechnen für das Diagramm, ohne Oberfläche testbar. */
internal object ChartMath {
    /** Index des Punkts, dessen x-Lage [x] am nächsten ist; bei gleichem Abstand der spätere. Null ohne Punkte. */
    fun nearestIndex(xs: List<Float>, x: Float): Int? {
        if (xs.isEmpty()) return null
        var best = 0
        for (i in xs.indices) if (abs(xs[i] - x) <= abs(xs[best] - x)) best = i
        return best
    }

    /** Beschriftete Werte der Y-Achse, aufsteigend: immer der niedrigste und der höchste, dazwischen bis zu zwei Stufen. */
    fun ticks(min: Int, max: Int): List<Int> =
        listOf(min, min + (max - min) / 3, min + (max - min) * 2 / 3, max).distinct()

    /** x-Lage je Ereignis zwischen [left] und [right], nach dem Datum verteilt. */
    fun xPositions(days: List<Long>, left: Float, right: Float): List<Float> {
        if (days.isEmpty()) return emptyList()
        val t0 = days.first()
        val span = (days.last() - t0).coerceAtLeast(1).toFloat()
        return days.map { left + (it - t0) / span * (right - left) }
    }
}

@Composable
private fun ChartCard(events: List<HistoryEvent>) {
    if (events.size < 2) {
        InfoText("In diesem Zeitraum gibt es zu wenige Ereignisse für ein Diagramm.")
        return
    }
    val values = events.map { it.after }
    val min = values.min()
    val max = values.max()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val markColor = MaterialTheme.colorScheme.onSurface
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val bubbleStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.Bold)
    val ticks = ChartMath.ticks(min, max)
    // Platz links für die Beschriftung der Y-Achse (breitester Wert plus Abstand), rechts für den letzten Punkt.
    val axisWidth = with(LocalDensity.current) { ticks.maxOf { measurer.measure(it.toString(), axisStyle).size.width } + 8.dp.toPx() }
    val rightPad = with(LocalDensity.current) { 8.dp.toPx() }
    val days = events.map { it.date.toEpochDay() }
    // Gewähltes Ereignis (Antippen oder Ziehen); wechselt der Zeitraum, verfällt die Auswahl.
    var selected by remember(events) { mutableStateOf<Int?>(null) }
    val sel = selected?.let { events.getOrNull(it) }

    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(sel?.let { fmt(it.date) } ?: "Aktuell", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text((sel?.after ?: values.last()).toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (sel != null) {
                    val (delta, color) = deltaText(sel.delta)
                    Text("Änderung an diesem Tag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(delta, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Höchstwert im Zeitraum", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(max.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().padding(top = 12.dp).height(220.dp).pointerInput(events, axisWidth) {
                fun pick(x: Float) {
                    selected = ChartMath.nearestIndex(ChartMath.xPositions(days, axisWidth, size.width - rightPad), x)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        pick(change.position.x)
                        change.consume()
                    }
                }
            },
        ) {
            val span = (max - min).coerceAtLeast(10).toFloat()
            val pad = span * 0.12f
            val lo = min - pad
            val hi = max + pad
            fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
            val xs = ChartMath.xPositions(days, axisWidth, size.width - rightPad)
            // Y-Achse: Linie und Wert je Stufe, mindestens niedrigster und höchster Wert.
            for (t in ticks) {
                val gy = y(t.toFloat())
                drawLine(gridColor, Offset(axisWidth, gy), Offset(size.width, gy), strokeWidth = 1f)
                val label = measurer.measure(t.toString(), axisStyle)
                drawText(label, topLeft = Offset(0f, (gy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
            }
            drawLine(gridColor, Offset(axisWidth, 0f), Offset(axisWidth, size.height), strokeWidth = 1f)
            val path = Path()
            events.forEachIndexed { i, e ->
                val py = y(e.after.toFloat())
                if (i == 0) path.moveTo(xs[i], py) else path.lineTo(xs[i], py)
            }
            drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            val i = selected
            if (i != null && i in events.indices) {
                // Markierung: senkrechte Linie, Punkt und der Wert mit Datum als Schild über dem Punkt.
                val px = xs[i]
                val py = y(events[i].after.toFloat())
                drawLine(markColor, Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
                drawCircle(markColor, 13f, Offset(px, py))
                drawCircle(lineColor, 9f, Offset(px, py))
                val text = measurer.measure("${events[i].after} · ${fmt(events[i].date)}", bubbleStyle)
                val bw = text.size.width + 24f
                val bh = text.size.height + 12f
                val bx = (px - bw / 2f).coerceIn(axisWidth, (size.width - bw).coerceAtLeast(axisWidth))
                val by = if (py - bh - 20f >= 0f) py - bh - 20f else (py + 20f).coerceAtMost(size.height - bh)
                drawRoundRect(bubbleColor, Offset(bx, by), Size(bw, bh), CornerRadius(12f, 12f))
                drawText(text, topLeft = Offset(bx + 12f, by + 6f))
            } else {
                val last = events.lastIndex
                drawCircle(lineColor, 11f, Offset(xs[last], y(events[last].after.toFloat())))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = with(LocalDensity.current) { axisWidth.toDp() }), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(events.first().date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(events.last().date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (sel == null) "Diagramm antippen oder darüber ziehen, um einzelne Werte zu sehen." else splitName(sel).second,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** Auswertung des gewählten Zeitraums: Siegquote, Mannschaftsspiele und die größten TTR-Änderungen. */
@Composable
private fun StatsCard(events: List<HistoryEvent>) {
    if (events.isEmpty()) {
        InfoText("In diesem Zeitraum gibt es keine Ereignisse.")
        return
    }
    val s = Statistics.summarize(events)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard {
        Text("Siegquote Einzel", style = MaterialTheme.typography.bodySmall, color = muted)
        Text(s.matchWinRate?.let { "$it %" } ?: "–", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("${s.matchesWon} gewonnen · ${s.matchesLost} verloren", style = MaterialTheme.typography.bodyMedium, color = muted)
    }
    AppCard {
        Text("Mannschaftsspiele", style = MaterialTheme.typography.bodySmall, color = muted)
        Text("${s.meetings} gespielt", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("${s.meetingWins} Siege")
            Pill("${s.meetingDraws} unentschieden")
            Pill("${s.meetingLosses} Niederlagen")
        }
    }
    AppCard {
        Text("Größte TTR-Änderung", style = MaterialTheme.typography.bodySmall, color = muted)
        ExtremeLine(s.biggestGain)
        ExtremeLine(s.biggestLoss)
    }
}

@Composable
private fun ExtremeLine(e: HistoryEvent?) {
    if (e == null) {
        Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (delta, color) = deltaText(e.delta)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(delta, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Column(Modifier.weight(1f)) {
            Text(fmt(e.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(splitName(e).second, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

private fun fmt(d: LocalDate) = "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
