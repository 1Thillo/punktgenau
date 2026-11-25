package app.punktgenau.data

import app.punktgenau.domain.HeadToHead
import app.punktgenau.domain.HeadToHeadMath
import java.time.LocalDate

/** Ergebnis der Bilanzsuche: die Bilanz und wie viele Spielberichte geprüft wurden (ohne Abruffehler). */
data class HeadToHeadResult(val total: HeadToHead, val reportsChecked: Int, val reportsFailed: Int)

/**
 * Bilanz zweier Spieler: sucht zu den Mannschaftsspielen von [aEvents] der laufenden Saison die Spielberichte
 * (über [findReport]) und zählt die Begegnungen mit [bId]. Der Verlauf von [aId] reicht, weil jede gemeinsame
 * Begegnung dort als Mannschaftsspiel steht. [onProgress] meldet (erledigt, gesamt).
 */
suspend fun loadHeadToHead(
    aId: String,
    bId: String,
    aEvents: List<HistoryEvent>,
    findReport: suspend (HistoryEvent) -> ReportLink?,
    spielbericht: SpielberichtRepository,
    today: LocalDate = LocalDate.now(),
    onProgress: (Int, Int) -> Unit = { _, _ -> },
): HeadToHeadResult {
    // Saison wie im Verlauf: ab 1. Juli.
    val seasonStart = LocalDate.of(if (today.monthValue >= 7) today.year else today.year - 1, 7, 1)
    val meetings = aEvents.filter { it.type == "meeting" && it.date >= seasonStart }
    // Mehrere Ereignisse können auf denselben Bericht zeigen: jeden Bericht nur einmal laden.
    val links = LinkedHashMap<String, ReportLink>()
    meetings.forEachIndexed { i, e ->
        onProgress(i, meetings.size)
        findReport(e)?.let { links.putIfAbsent(it.url, it) }
    }
    var total = HeadToHead.NONE
    var checked = 0
    var failed = 0
    val list = links.values.toList()
    list.forEachIndexed { i, link ->
        onProgress(meetings.size + i, meetings.size + list.size)
        when (val r = spielbericht.load(link.url)) {
            is Result.Ok -> {
                checked++
                total += HeadToHeadMath.count(r.value.games, aId, bId)
            }
            else -> failed++
        }
    }
    onProgress(meetings.size + list.size, meetings.size + list.size)
    return HeadToHeadResult(total, checked, failed)
}
