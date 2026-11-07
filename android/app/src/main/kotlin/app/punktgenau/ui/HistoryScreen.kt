package app.punktgenau.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.HistoryEvent
import java.time.LocalDate

/** TTR-Verlauf: Liste der Ereignisse (neueste zuerst) oder Diagramm. */
@Composable
fun HistoryScreen(
    events: List<HistoryEvent>,
    loggedIn: Boolean,
    modifier: Modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
    showTitle: Boolean = true,
) {
    var view by remember { mutableIntStateOf(0) } // 0 = Spiele, 1 = Diagramm
    var range by remember { mutableIntStateOf(0) } // 0 = Saison, 1 = 1 Jahr, 2 = Alles
    val today = LocalDate.now()
    val seasonStart = LocalDate.of(if (today.monthValue >= 7) today.year else today.year - 1, 7, 1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) ScreenTitle("TTR-Verlauf")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == 0, { view = 0 }, { Text("Spiele") })
            FilterChip(view == 1, { view = 1 }, { Text("Diagramm") })
        }
        if (events.isEmpty()) {
            InfoText(if (loggedIn) "Noch keine Ereignisse geladen. Auf dem Start-Reiter aktualisieren." else "Bitte zuerst anmelden.")
            return@Column
        }
        if (view == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(range == 0, { range = 0 }, { Text("Saison") })
                FilterChip(range == 1, { range = 1 }, { Text("1 Jahr") })
                FilterChip(range == 2, { range = 2 }, { Text("Alles") })
            }
            val from = when (range) { 0 -> seasonStart; 1 -> today.minusYears(1); else -> LocalDate.MIN }
            ChartCard(events.filter { it.date >= from })
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(events.asReversed()) { e -> EventCard(e) }
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun EventCard(e: HistoryEvent) {
    val (league, teams) = splitName(e)
    val (delta, color) = deltaText(e.delta)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(54.dp)) {
                Text("%02d.%02d.".format(e.date.dayOfMonth, e.date.monthValue), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(e.date.year.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
    }
}

/** Liga und Paarung aus "Liga | Heim : Gast"; sonstige Ereignisse behalten ihren Namen. */
internal fun splitName(e: HistoryEvent): Pair<String?, String> {
    val parts = e.name.split(" | ", limit = 2)
    return if (e.type == "meeting" && parts.size == 2) parts[0] to parts[1]
    else (if (e.type == "correction") "Korrektur" else null) to e.name.replace("Spielstaerkezuwachs", "Spielstärkezuwachs")
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
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Aktuell", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(values.last().toString(), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Höchstwert im Zeitraum", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(max.toString(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 12.dp)) {
            val span = (max - min).coerceAtLeast(10).toFloat()
            val pad = span * 0.12f
            val lo = min - pad
            val hi = max + pad
            fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
            val t0 = events.first().date.toEpochDay().toFloat()
            val t1 = events.last().date.toEpochDay().toFloat().coerceAtLeast(t0 + 1)
            fun x(d: LocalDate) = (d.toEpochDay() - t0) / (t1 - t0) * size.width
            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            val path = Path()
            events.forEachIndexed { i, e ->
                val px = x(e.date)
                val py = y(e.after.toFloat())
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
            val last = events.last()
            drawCircle(lineColor, 11f, Offset(x(last.date), y(last.after.toFloat())))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(events.first().date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${min}–${max}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(events.last().date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fmt(d: LocalDate) = "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
