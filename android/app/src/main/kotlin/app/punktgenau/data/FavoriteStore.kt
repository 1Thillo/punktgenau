package app.punktgenau.data

import android.content.SharedPreferences

/** Einfacher Speicher für eine Menge von Zeichenketten; trennt den Store von Android, damit er testbar bleibt. */
interface StringSetStorage {
    fun get(): Set<String>
    fun put(values: Set<String>)
}

/** Gemerkte Spielerinnen und Spieler, nur lokal auf dem Gerät. Ohne Anmeldung nutzbar, kein Konto- oder Cloud-Bezug. */
class FavoriteStore(private val storage: StringSetStorage) {

    constructor(prefs: SharedPreferences) : this(SharedPrefsStringSet(prefs, KEY))

    /** Alle Favoriten, nach Namen sortiert. */
    fun load(): List<PlayerLink> = storage.get().mapNotNull(::decode).sortedBy { it.name.lowercase() }

    fun contains(nuId: String): Boolean = load().any { it.nuId == nuId }

    /** Merkt die Person samt Mannschaft (falls bekannt); ein vorhandener Eintrag mit derselben Kennung wird ersetzt. */
    fun add(player: PlayerLink) = save(load().filter { it.nuId != player.nuId } + player)

    fun remove(nuId: String) = save(load().filter { it.nuId != nuId })

    /** Merkt die Person oder entfernt sie wieder; liefert den neuen Zustand (true = Favorit). */
    fun toggle(player: PlayerLink): Boolean =
        if (contains(player.nuId)) {
            remove(player.nuId)
            false
        } else {
            add(player)
            true
        }

    private fun save(players: List<PlayerLink>) = storage.put(players.map(::encode).toSet())

    companion object {
        private const val KEY = "favorite_players"
        private const val SEP = "\t"

        private fun clean(s: String?): String = s.orEmpty().replace(SEP, " ").replace("\n", " ")

        /**
         * Felder: Kennung, Name, Verein, Vereinsnummer, danach die Mannschaft (Verband, Saison, Gruppe, Liga,
         * Mannschafts-Kennung, Mannschaftsname, Vereinsnummer der Mannschaft). Ohne Mannschaft bleiben diese leer.
         */
        internal fun encode(p: PlayerLink): String {
            val t = p.team
            return listOf(
                p.nuId, p.name, p.club, p.clubNr,
                t?.group?.association, t?.group?.season, t?.group?.groupId, t?.group?.name,
                t?.teamId, t?.name, t?.clubNr,
            ).joinToString(SEP) { clean(it) }
        }

        internal fun decode(s: String): PlayerLink? {
            val parts = s.split(SEP)
            if (parts.size < 2 || parts[0].isBlank()) return null
            fun at(i: Int) = parts.getOrNull(i)?.ifBlank { null }
            val association = at(4)
            val season = at(5)
            val groupId = at(6)
            val teamId = at(8)
            // Einträge aus der ersten Version haben nur vier Felder; dann ist keine Mannschaft bekannt.
            val team = if (association != null && season != null && groupId != null && teamId != null) {
                TeamRef(GroupRef(association, season, groupId, at(7) ?: at(9).orEmpty()), teamId, at(9).orEmpty(), at(10))
            } else {
                null
            }
            return PlayerLink(nuId = parts[0], name = parts[1], club = at(2), clubNr = at(3), team = team)
        }
    }
}

/** Die gespeicherte Mannschaft gilt nur in ihrer Saison; aus einer anderen Saison wird sie weggelassen. */
fun PlayerLink.teamOnlyIn(season: String): PlayerLink = if (team?.group?.season == season) this else copy(team = null)

private class SharedPrefsStringSet(private val prefs: SharedPreferences, private val key: String) : StringSetStorage {
    // Kopie: Die von getStringSet gelieferte Menge darf nicht verändert werden.
    override fun get(): Set<String> = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toSet()
    override fun put(values: Set<String>) {
        prefs.edit().putStringSet(key, values).apply()
    }
}
