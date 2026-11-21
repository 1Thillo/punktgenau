package app.punktgenau.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.FavoriteStore
import app.punktgenau.data.HistoryEvent
import app.punktgenau.data.PlayerHistoryOutcome
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.Season
import app.punktgenau.data.teamOnlyIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

/** Anzahl der letzten Ereignisse im kleinen Verlaufsdiagramm einer Zeile. */
private const val SPARK_EVENTS = 20

/**
 * Gemerkte Spielerinnen und Spieler mit TTR, Q-TTR, letzter Änderung und kleinem Verlaufsdiagramm.
 * Antippen öffnet die Person (mit Mannschaft, falls aus der laufenden Saison bekannt), „Entfernen“ nimmt sie heraus.
 * Die Werte kommen aus dem Verlauf der Person: höchstens fünf Abrufe zugleich, bekannte Verläufe aus dem Zwischenspeicher.
 */
@Composable
fun FavoritesScreen(store: FavoriteStore, players: PlayerRepository, onOpen: (PlayerLink) -> Unit, onClose: () -> Unit) {
    // Wird bei jedem Öffnen neu gelesen, damit Änderungen auf der Spielerseite sofort sichtbar sind.
    val favorites = remember { mutableStateListOf<PlayerLink>().also { it.addAll(store.load()) } }
    val outcomes = remember { mutableStateMapOf<String, PlayerHistoryOutcome>() }
    val season = remember { Season.label(LocalDate.now()) }
    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        coroutineScope {
            val gate = Semaphore(5)
            favorites.toList().map { p ->
                async { gate.withPermit { outcomes[p.nuId] = players.history(p.nuId) } }
            }.awaitAll()
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle("Favoriten")
        if (favorites.isEmpty()) {
            InfoText("Noch keine Favoriten. Auf der Seite einer Person „Als Favorit merken“ tippen.")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites.toList(), key = { it.nuId }) { p ->
                FavoriteCard(
                    player = p,
                    outcome = outcomes[p.nuId],
                    onOpen = { onOpen(p.teamOnlyIn(season)) },
                    onRemove = {
                        store.remove(p.nuId)
                        favorites.removeAll { it.nuId == p.nuId }
                    },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FavoriteCard(player: PlayerLink, outcome: PlayerHistoryOutcome?, onOpen: () -> Unit, onRemove: () -> Unit) {
    val history = (outcome as? PlayerHistoryOutcome.Ok)?.history
    val main = history?.ttr ?: history?.qttr
    AppCard {
        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "${player.name} ansehen", onClick = onOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(player.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val sub = listOfNotNull(player.club ?: history?.clubName, player.team?.name).distinct().joinToString(" · ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (main != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(main.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (history?.ttr != null) "TTR" else "Q-TTR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when (outcome) {
                null -> InfoText("Lade Werte …")
                PlayerHistoryOutcome.NeedsLogin -> InfoText("Für die Werte bitte anmelden.")
                is PlayerHistoryOutcome.Failed -> InfoText(outcome.message)
                is PlayerHistoryOutcome.Ok -> {
                    val recent = outcome.history.events.sortedBy { it.date }.takeLast(SPARK_EVENTS)
                    if (recent.size >= 2) Sparkline(recent, Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (outcome.history.ttr != null) outcome.history.qttr?.let { Pill("Q-TTR $it") }
                        recent.lastOrNull()?.let { last ->
                            val (txt, color) = deltaText(last.delta)
                            Pill("Zuletzt $txt", color = color.copy(alpha = 0.12f), textColor = color)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onRemove) { Text("Entfernen") }
        }
    }
}

/** Kleiner TTR-Verlauf ohne Achsen: Linie über die Werte nach jedem Ereignis, letzter Punkt markiert. */
@Composable
private fun Sparkline(events: List<HistoryEvent>, modifier: Modifier) {
    val values = events.map { it.after }
    val min = values.min()
    val max = values.max()
    val color = MaterialTheme.colorScheme.primary
    val description = "Verlauf der letzten ${events.size} Ereignisse, von ${values.first()} auf ${values.last()}"
    Canvas(modifier.semantics { contentDescription = description }) {
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        fun point(i: Int): Offset {
            val x = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
            val y = if (max == min) pad + h / 2 else pad + h * (max - values[i]) / (max - min).toFloat()
            return Offset(x, y)
        }
        val path = Path().apply {
            values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = point(values.lastIndex))
    }
}
