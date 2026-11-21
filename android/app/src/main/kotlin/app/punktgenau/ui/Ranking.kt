package app.punktgenau.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.punktgenau.data.GroupRef
import app.punktgenau.data.PlayerLink
import app.punktgenau.data.PlayerRepository
import app.punktgenau.data.RankEntry
import app.punktgenau.data.Ranking
import app.punktgenau.data.RatingMode
import app.punktgenau.data.RatingOutcome
import app.punktgenau.data.ReportLink
import app.punktgenau.data.TeamRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Wege von einem Bildschirm zum nächsten: Person, Mannschaft, Verein (Vereinsnummer), Liga, Spielbericht. */
class Links(
    val player: (PlayerLink) -> Unit,
    val team: (TeamRef) -> Unit,
    val club: (String) -> Unit,
    val league: (GroupRef) -> Unit,
    val report: (ReportLink) -> Unit,
)

/** Gewählter Wert der Ranglisten. Gilt für alle Ranglisten, solange die App läuft. */
object RatingModeState {
    var mode by mutableStateOf(RatingMode.TTR)
}

/** Umschalter zwischen TTR und QTTR, als Segmente in voller Breite. */
@Composable
fun RatingModeChips() {
    val modes = RatingMode.entries
    Segmented(modes.map { it.label }, modes.indexOf(RatingModeState.mode)) { RatingModeState.mode = modes[it] }
}

/**
 * Holt TTR und QTTR zu den [seeds] (höchstens fünf Abrufe zugleich; schon bekannte Verläufe kommen aus dem
 * Zwischenspeicher). Einträge ohne jeden Wert fallen weg.
 */
suspend fun loadRatings(
    players: PlayerRepository,
    seeds: List<RankEntry>,
    onEntry: (RankEntry) -> Unit,
    onProgress: (done: Int, total: Int) -> Unit,
) = coroutineScope {
    val gate = Semaphore(5)
    var done = 0
    seeds.map { seed ->
        async {
            gate.withPermit {
                val r = players.ratings(seed.nuId)
                if (r is RatingOutcome.Values && (r.ttr != null || r.qttr != null)) onEntry(seed.copy(ttr = r.ttr, qttr = r.qttr))
                done++
                onProgress(done, seeds.size)
            }
        }
    }.awaitAll()
}

/** Zeilen einer Rangliste nach dem gewählten Wert; ein Tipp öffnet die Person. */
fun LazyListScope.rankingItems(entries: List<RankEntry>, mode: RatingMode, onOpenPlayer: (PlayerLink) -> Unit) {
    val sorted = Ranking.sorted(entries, mode)
    items(sorted.size) { i ->
        val e = sorted[i]
        val value = Ranking.value(e, mode)
        val other = if (mode == RatingMode.TTR) e.qttr?.let { "QTTR $it" } else e.ttr?.let { "TTR $it" }
        ListCard(
            title = e.name,
            subtitle = e.team,
            onClick = { onOpenPlayer(PlayerLink(e.nuId, e.name, clubNr = e.clubNr, team = e.teamRef)) },
            leading = { if (value != null) RankBadge(i + 1) else Text("–", modifier = Modifier.padding(horizontal = 14.dp)) },
            trailing = { TrailingNumber(value?.toString() ?: "–", other) },
        )
    }
}
