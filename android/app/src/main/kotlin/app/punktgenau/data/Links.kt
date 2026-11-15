package app.punktgenau.data

/** Verweis auf eine Mannschaft in ihrer Gruppe; [clubNr] nur, wenn die Quelle die Vereinsnummer nennt. */
data class TeamRef(val group: GroupRef, val teamId: String, val name: String, val clubNr: String? = null)

/** Verweis auf eine Person. [club] ist der Vereinsname, [clubNr] die Vereinsnummer; beides je nach Quelle. */
data class PlayerLink(
    val nuId: String,
    val name: String,
    val club: String? = null,
    val clubNr: String? = null,
    val team: TeamRef? = null,
)

/** Verweis auf einen Spielbericht mit beiden Mannschaften, soweit bekannt. */
data class ReportLink(
    val url: String,
    val title: String,
    val running: Boolean,
    val home: TeamRef? = null,
    val away: TeamRef? = null,
)

/** Welcher Wert eine Rangliste ordnet. */
enum class RatingMode(val label: String) { TTR("TTR"), QTTR("QTTR") }

/** Eintrag einer TTR-Rangliste mit beiden Werten, damit das Umschalten keinen neuen Abruf braucht. */
data class RankEntry(
    val nuId: String,
    val name: String,
    val team: String,
    val ttr: Int?,
    val qttr: Int?,
    val clubNr: String? = null,
    val teamRef: TeamRef? = null,
)

object Ranking {
    fun value(e: RankEntry, mode: RatingMode): Int? = if (mode == RatingMode.TTR) e.ttr else e.qttr

    /** Absteigend nach dem gewählten Wert; wer ihn nicht hat, steht in gleicher Reihenfolge am Ende. */
    fun sorted(entries: List<RankEntry>, mode: RatingMode): List<RankEntry> {
        val (with, without) = entries.partition { value(it, mode) != null }
        return with.sortedByDescending { value(it, mode) } + without
    }
}
