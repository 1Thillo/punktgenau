package app.punktgenau.data

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Ein Spiel als Kalendertermin: Titel, Liga, Verweis auf den Spielbericht. */
data class KalenderTermin(
    /** Stabile Kennung, damit ein erneuter Export denselben Termin ergibt. */
    val uid: String,
    val start: LocalDateTime,
    val home: String,
    val away: String,
    val league: String?,
    val url: String?,
)

/**
 * Kalender-Export der Spieltag-Ansicht als iCalendar-Datei (RFC 5545). Reine Funktionen ohne Android-Abhängigkeit,
 * damit sie auf dem JVM testbar sind.
 */
object SpieltagKalender {
    /** Angenommene Dauer eines Spiels; der Spielplan nennt kein Ende. */
    const val DURATION_HOURS = 2L

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** Termin eines Spiels aus der Spieltag-Ansicht; null ohne lesbare Anstoßzeit. */
    fun termin(m: SpieltagMatch, now: LocalDateTime = MatchTime.now()): KalenderTermin? {
        val a = m.appointment
        val start = MatchTime.local(a.date) ?: return null
        val own = Spieltag.ownRef(m.team, a)
        // Dieselben Namen und derselbe Link wie auf der Spielkarte.
        val report = if (own.group.groupId.isNotBlank()) Spieltag.reportLink(own, a, now) else null
        val ownName = a.ownName ?: own.name
        val opponent = a.opponentName
        val home = report?.home?.name ?: if (a.home == false) opponent else ownName
        val away = report?.away?.name ?: if (a.home == false) ownName else opponent
        return KalenderTermin(
            uid = "${m.team.id}-${a.meetingId ?: a.date}-${a.opponentTeamId}@punktgenau",
            start = start,
            home = home,
            away = away,
            league = m.team.league?.takeIf { it.isNotBlank() },
            url = report?.url,
        )
    }

    /** Eine .ics-Datei mit einem VEVENT je Termin; ohne Termine entsteht ein leerer Kalender. */
    fun ics(termine: List<KalenderTermin>, stamp: Instant = Instant.now()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//punktgenau//Spieltag//DE",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
        )
        val dtstamp = utcStamp.format(stamp)
        for (t in termine) {
            val start = t.start.atZone(zone).toInstant()
            val end = start.plus(DURATION_HOURS, ChronoUnit.HOURS)
            lines += "BEGIN:VEVENT"
            lines += "UID:${escape(t.uid)}"
            lines += "DTSTAMP:$dtstamp"
            lines += "DTSTART:${utcStamp.format(start)}"
            lines += "DTEND:${utcStamp.format(end)}"
            lines += "SUMMARY:${escape("${t.home} gegen ${t.away}")}"
            val description = buildList {
                t.league?.let { add("Liga: $it") }
                t.url?.let { add("Spielbericht: $it") }
            }
            if (description.isNotEmpty()) lines += "DESCRIPTION:${escape(description.joinToString("\n"))}"
            t.league?.let { lines += "CATEGORIES:${escape(it)}" }
            t.url?.let { lines += "URL:$it" }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.flatMap { fold(it) }.joinToString("") { "$it\r\n" }
    }

    /** Textwert nach RFC 5545: Backslash, Semikolon, Komma und Zeilenumbruch maskieren. */
    internal fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\n", "\\n")

    /** Zeilen mit höchstens 75 Oktetten; Fortsetzungszeilen beginnen mit einem Leerzeichen. */
    internal fun fold(line: String): List<String> {
        val out = ArrayList<String>()
        var cur = StringBuilder()
        var bytes = 0
        var limit = 75
        line.codePoints().forEach { cp ->
            val s = String(Character.toChars(cp))
            val b = s.toByteArray(Charsets.UTF_8).size
            if (bytes + b > limit) {
                out += cur.toString()
                cur = StringBuilder()
                bytes = 0
                limit = 74
            }
            cur.append(s)
            bytes += b
        }
        out += cur.toString()
        return out.mapIndexed { i, s -> if (i == 0) s else " $s" }
    }
}
