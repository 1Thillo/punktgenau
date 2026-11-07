package app.punktgenau.data

import android.content.SharedPreferences

/** Gemerkte Mannschaften (auch fremder Vereine), lokal auf dem Gerät. Ohne Anmeldung nutzbar. */
class FollowStore(private val prefs: SharedPreferences) {

    fun load(): List<TeamInfo> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).mapNotNull { decode(it) }.sortedBy { it.name }

    fun add(team: TeamInfo) = save(load().filter { it.id != team.id } + team)

    fun remove(teamId: String) = save(load().filter { it.id != teamId })

    private fun save(teams: List<TeamInfo>) {
        prefs.edit().putStringSet(KEY, teams.map { encode(it) }.toSet()).apply()
    }

    private fun encode(t: TeamInfo): String =
        listOf(t.id, t.name, t.league.orEmpty(), t.season.orEmpty(), t.groupId.orEmpty(), t.organization.orEmpty(), t.club.orEmpty())
            .joinToString(SEP)

    private fun decode(s: String): TeamInfo? {
        val p = s.split(SEP)
        if (p.size < 6 || p[0].isBlank()) return null
        return TeamInfo(
            id = p[0],
            name = p[1],
            league = p[2].ifBlank { null },
            season = p[3].ifBlank { null },
            groupId = p[4].ifBlank { null },
            organization = p[5].ifBlank { null },
            club = p.getOrNull(6)?.ifBlank { null },
        )
    }

    companion object {
        private const val KEY = "followed_teams"
        private const val SEP = "\t"
    }
}
