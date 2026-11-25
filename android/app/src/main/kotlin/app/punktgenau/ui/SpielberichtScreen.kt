package app.punktgenau.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.punktgenau.data.GameLine
import app.punktgenau.data.LIVE_INTERVAL_MS
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.ReportLink
import app.punktgenau.data.Result
import app.punktgenau.data.SpielberichtReport
import app.punktgenau.data.SpielberichtRepository
import app.punktgenau.data.TeamRef
import kotlinx.coroutines.delay

/** Kopfzeile im Live-Modus: Sekunden bis zur nächsten Aktualisierung; solange noch nichts geplant ist oder sie läuft, "WIRD AKTUALISIERT". */
private fun liveLabel(nextAt: Long, now: Long): String {
    if (nextAt == 0L) return "LIVE · WIRD AKTUALISIERT"
    val seconds = ((nextAt - now + 999) / 1000).coerceAtLeast(0)
    return if (seconds == 0L) "LIVE · WIRD AKTUALISIERT" else "LIVE · NÄCHSTES UPDATE IN $seconds S"
}

/** Gewinner einer Zeile aus dem Satzstand "3:1": true = Heim, false = Gast, null = offen oder unlesbar. */
private fun homeWon(sets: String): Boolean? {
    val p = sets.split(":").mapNotNull { it.trim().toIntOrNull() }
    if (p.size != 2 || p[0] == p[1]) return null
    return p[0] > p[1]
}

/** Namen einer Seite (Einzel: einer, Doppel: zwei); ein Tipp auf einen Namen mit Kennung öffnet die Person. */
@Composable
private fun PlayerNames(names: List<String>, ids: List<String?>, team: TeamRef?, links: Links, winner: Boolean) {
    // Jeder Name in einer eigenen Zeile: Namen werden nicht mehr gekürzt, sondern dürfen umbrechen.
    Column {
        names.forEachIndexed { i, name ->
            val id = ids.getOrNull(i)
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
                color = if (id != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = if (id != null) {
                    Modifier.clickable { links.player(PlayerLink(id, name, clubNr = team?.clubNr, team = team)) }
                } else {
                    Modifier
                },
            )
        }
    }
}

/** Eine Spielzeile: Position links, beide Seiten untereinander (Sieger fett), Satzstand groß rechts, Sätze dezent darunter. */
@Composable
private fun GameCard(g: GameLine, link: ReportLink, links: Links) {
    val win = homeWon(g.sets)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.width(60.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(g.rank, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlayerNames(g.home, g.homeIds, link.home, links, winner = win == true)
                PlayerNames(g.away, g.awayIds, link.away, links, winner = win == false)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ScoreBox(g.sets, strong = win != null)
                Text("Stand ${g.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (g.setScores.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                g.setScores.forEach { s ->
                    Text(
                        s,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
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
    // Zeitpunkt der nächsten Aktualisierung und die aktuelle Zeit, daraus die Anzeige des Countdowns.
    var nextAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val url = link.url
    val live = link.running
    BackHandler(onBack = onClose)
    LaunchedEffect(url) { state = repo.load(url, fresh = live) }
    // Sekündlich die Zeit aktualisieren, solange das Spiel läuft.
    LaunchedEffect(live) {
        while (live) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    // Laufendes Spiel: Bericht alle paar Sekunden neu holen, bis der Bildschirm verlassen wird.
    LaunchedEffect(liveTick, live) {
        if (!live) return@LaunchedEffect
        nextAt = System.currentTimeMillis() + LIVE_INTERVAL_MS
        delay(LIVE_INTERVAL_MS)
        val r = repo.load(url, fresh = true)
        if (r is Result.Ok) state = r
        liveTick++
    }

    val report = (state as? Result.Ok)?.value
    val totals = report?.totals

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BackBar(onClose) {
                AccentAction("Im Browser öffnen ↗", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) })
            }
        }
        item { ScreenTitle(if (live) "Live-Spielbericht" else "Spielbericht") }
        item {
            HeroCard {
                Text(
                    if (live) liveLabel(nextAt, now) else "ERGEBNIS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                if (link.home != null || link.away != null) {
                    // Die Mannschaften links und rechts, der Gesamtstand groß in der Mitte; ein Tipp öffnet die Mannschaft.
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroChoice(link.home?.name ?: "–", link.home?.let { t -> { links.team(t) } }, Modifier.weight(1f).fillMaxHeight())
                        Text(
                            totals?.total ?: report?.games?.lastOrNull()?.total ?: "–",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        HeroChoice(link.away?.name ?: "–", link.away?.let { t -> { links.team(t) } }, Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Text(link.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        totals?.total ?: report?.games?.lastOrNull()?.total ?: "–",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                if (totals != null) {
                    HeroStrip {
                        HeroStat("Spiele", totals.total)
                        HeroStat("Sätze", totals.sets)
                        totals.balls?.let { HeroStat("Bälle", it) }
                    }
                }
            }
        }
        if (link.home != null || link.away != null) item { InfoText(link.title) }
        when (val s = state) {
            null -> item { LoadingBox() }
            Result.NeedsLogin -> item { InfoText("Bitte zuerst anmelden.") }
            is Result.Failed -> item { InfoText(s.message) }
            is Result.Ok -> if (s.value.games.isEmpty()) {
                item {
                    AppCard {
                        InfoText(
                            if (live) {
                                "Noch keine Ergebnisse eingetragen. Viele Mannschaften tragen den Bericht erst nach dem Spiel ein. " +
                                    "Solange das Spiel läuft, wird hier alle 45 Sekunden neu geladen."
                            } else {
                                "Für dieses Spiel ist noch nichts eingetragen."
                            },
                        )
                    }
                }
            } else {
                item { SectionLabel("Spiele") }
                items(s.value.games) { g -> GameCard(g, link, links) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
