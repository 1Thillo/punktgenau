package app.punktgenau

import android.app.Application
import app.punktgenau.data.ProfileRepository
import app.punktgenau.data.session.EncryptedFileSessionStorage
import app.punktgenau.data.session.KeystoreKey
import app.punktgenau.data.session.PersistentCookieJar
import app.punktgenau.data.session.SessionManager
import app.punktgenau.data.session.buildHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

class PunktgenauApp : Application() {
    val baseUrl = "https://www.mytischtennis.de/".toHttpUrl()

    val jar by lazy {
        PersistentCookieJar(EncryptedFileSessionStorage(File(filesDir, "session.bin"), KeystoreKey.cipher()))
    }
    val session by lazy { SessionManager(jar, buildHttpClient(jar), baseUrl) }
    val playerRepo by lazy { app.punktgenau.data.PlayerRepository(session, baseUrl) }
    val clickTt by lazy { app.punktgenau.data.ClickTtRepository(session, baseUrl) }
    val teams by lazy { app.punktgenau.data.TeamRepository(session, baseUrl) }
    val follow by lazy { app.punktgenau.data.FollowStore(getSharedPreferences("follow", MODE_PRIVATE)) }
    val favorites by lazy { app.punktgenau.data.FavoriteStore(getSharedPreferences("favorites", MODE_PRIVATE)) }
    val spielbericht by lazy { app.punktgenau.data.SpielberichtRepository(session) }
    val spieltag by lazy { app.punktgenau.data.SpieltagRepository(teams, follow) }
    val matchFinder by lazy { app.punktgenau.data.MatchFinder(teams) }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    /** Darstellung; hell ist der Standard. Wird in den Einstellungen gespeichert. */
    var themeMode: app.punktgenau.ui.ThemeMode
        get() = runCatching { app.punktgenau.ui.ThemeMode.valueOf(prefs.getString("theme", "LIGHT") ?: "LIGHT") }
            .getOrDefault(app.punktgenau.ui.ThemeMode.LIGHT)
        set(value) { prefs.edit().putString("theme", value.name).apply() }

    val clubs by lazy { app.punktgenau.data.ClubRepository(session, baseUrl) }
    val transfers by lazy { app.punktgenau.data.TransferRepository(session, baseUrl) }
    val advancedSearch by lazy { app.punktgenau.data.AdvancedSearchRepository(session, baseUrl) }
    val profiles by lazy { ProfileRepository(session, baseUrl) }

    /** Letzte Namenssuchen, lokal auf dem Gerät. */
    val searchHistory by lazy { app.punktgenau.data.SearchHistory(getSharedPreferences("search", MODE_PRIVATE)) }

    /** Spielbenachrichtigungen: eingeschaltet oder nicht (Standard: aus). */
    val alertsEnabled: Boolean
        get() = prefs.getBoolean("match_alerts", false)

    /** Ist der Ist-Stand beim ersten Lauf nach dem Einschalten schon gemerkt? */
    val alertsBaselined: Boolean
        get() = prefs.getBoolean("match_alerts_baselined", false)

    fun markAlertsBaselined() {
        prefs.edit().putBoolean("match_alerts_baselined", true).apply()
    }

    fun setAlertsEnabled(enabled: Boolean) {
        // Beim Einschalten wird der Ist-Stand neu gemerkt, damit nichts Altes gemeldet wird.
        prefs.edit().putBoolean("match_alerts", enabled).putBoolean("match_alerts_baselined", false).apply()
        app.punktgenau.notify.MatchAlertWorker.setEnabled(this, enabled)
    }

    val alertStore by lazy {
        app.punktgenau.data.AlertStore(object : app.punktgenau.data.StringSetStorage {
            private val p by lazy { getSharedPreferences("alerts", MODE_PRIVATE) }
            override fun get(): Set<String> = (p.getStringSet("sent", emptySet()) ?: emptySet()).toSet()
            override fun put(values: Set<String>) {
                p.edit().putStringSet("sent", values).apply()
            }
        })
    }
}
