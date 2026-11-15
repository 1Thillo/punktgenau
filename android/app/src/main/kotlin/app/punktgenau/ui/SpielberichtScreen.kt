package app.punktgenau.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.punktgenau.data.LIVE_INTERVAL_MS
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.ReportLink
import app.punktgenau.data.Result
import app.punktgenau.data.SpielberichtReport
import app.punktgenau.data.SpielberichtRepository
import app.punktgenau.data.TeamRef
import kotlinx.coroutines.delay

/** Eine Summe: kleine Beschriftung oben, das Ergebnis darunter. */
@Composable
private fun TotalCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Namen einer Seite (Einzel: einer, Doppel: zwei); ein Tipp auf einen Namen mit Kennung öffnet die Person. */
@Composable
private fun PlayerNames(names: List<String>, ids: List<String?>, team: TeamRef?, links: Links) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEachIndexed { i, name ->
            val id = ids.getOrNull(i)
            if (i > 0) Text("/", style = MaterialTheme.typography.bodyMedium)
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (id != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (id != null) {
                    Modifier.weight(1f, fill = false).clickable { links.player(PlayerLink(id, name, clubNr = team?.clubNr, team = team)) }
                } else {
                    Modifier.weight(1f, fill = false)
                },
            )
        }
    }
}

/**
 * Spielbericht in der App: Einzel mit Namen und Ergebnis; der Browser bleibt als Ausweg. Ein laufendes
 * Spiel wird alle paar Sekunden neu geholt. Solange nichts eingetragen ist, sagt der Bildschirm das,
 * statt einen Fehler zu zeigen.
 */
@Composable
fun SpielberichtScreen(repo: SpielberichtRepository, link: ReportLink, links: Links, onClose: () -> Unit) {
    var state by remember { mutableStateOf<Result<SpielberichtReport>?>(null) }
    var liveTick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val url = link.url
    val live = link.running
    BackHandler(onBack = onClose)
    LaunchedEffect(url) { state = repo.load(url, fresh = live) }

    // Laufendes Spiel: Bericht alle paar Sekunden neu holen, bis der Bildschirm verlassen wird.
    LaunchedEffect(liveTick, live) {
        if (!live) return@LaunchedEffect
        delay(LIVE_INTERVAL_MS)
        val r = repo.load(url, fresh = true)
        if (r is Result.Ok) state = r
        liveTick++
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
        TextButton(onClose) { Text("Zurück") }
        ScreenTitle(if (live) "Live-Spielbericht" else "Spielbericht")
        InfoText(link.title)
        Spacer(Modifier.height(8.dp))
        if (link.home != null || link.away != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(link.home, link.away).forEach { t ->
                    OutlinedButton({ links.team(t) }, Modifier.weight(1f)) { Text(t.name, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        OutlinedButton(
            { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            Modifier.fillMaxWidth(),
        ) { Text("Im Browser öffnen") }
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            null -> LoadingBox()
            Result.NeedsLogin -> InfoText("Bitte zuerst anmelden.")
            is Result.Failed -> InfoText(s.message)
            is Result.Ok -> if (s.value.games.isEmpty()) {
                InfoText(
                    if (live) {
                        "Noch keine Ergebnisse eingetragen. Viele Mannschaften tragen den Bericht erst nach dem Spiel ein. " +
                            "Solange das Spiel läuft, wird hier alle 45 Sekunden neu geladen."
                    } else {
                        "Für dieses Spiel ist noch nichts eingetragen."
                    },
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.value.games) { g ->
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(g.rank, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                                Column(Modifier.weight(1f)) {
                                    PlayerNames(g.home, g.homeIds, link.home, links)
                                    PlayerNames(g.away, g.awayIds, link.away, links)
                                    if (g.setScores.isNotEmpty()) {
                                        Text(
                                            "Sätze: " + g.setScores.joinToString("   "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(g.sets, fontWeight = FontWeight.Bold)
                                    Text(g.total, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    s.value.totals?.let { t ->
                        item {
                            AppCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    t.balls?.let { TotalCell("Bälle", it) }
                                    TotalCell("Sätze", t.sets)
                                    TotalCell("Spiele", t.total)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
