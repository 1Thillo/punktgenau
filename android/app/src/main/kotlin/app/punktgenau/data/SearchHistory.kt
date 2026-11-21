package app.punktgenau.data

import android.content.SharedPreferences

/** Speicher für eine geordnete Liste von Texten. Trennt den Verlauf von Android, damit er testbar bleibt. */
interface TextListStorage {
    fun get(): List<String>
    fun put(values: List<String>)
}

/**
 * Die letzten Namenssuchen, neueste zuerst, lokal auf dem Gerät. Gleiche Suchen (ohne Groß-/Kleinschreibung)
 * stehen nur einmal, an die oberste Stelle gerückt. Begrenzt auf [MAX].
 */
class SearchHistory(private val storage: TextListStorage) {

    constructor(prefs: SharedPreferences) : this(PrefsTextList(prefs, KEY))

    fun load(): List<String> = storage.get()

    fun add(term: String) {
        val t = term.trim().replace('\n', ' ')
        if (t.isEmpty()) return
        val rest = storage.get().filterNot { it.equals(t, ignoreCase = true) }
        storage.put((listOf(t) + rest).take(MAX))
    }

    fun clear() = storage.put(emptyList())

    companion object {
        const val MAX = 10
        private const val KEY = "search_history"
    }
}

private class PrefsTextList(private val prefs: SharedPreferences, private val key: String) : TextListStorage {
    override fun get(): List<String> =
        prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    override fun put(values: List<String>) {
        prefs.edit().putString(key, values.joinToString("\n")).apply()
    }
}
