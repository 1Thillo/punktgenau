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
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    /** Darstellung; hell ist der Standard. Wird in den Einstellungen gespeichert. */
    var themeMode: app.punktgenau.ui.ThemeMode
        get() = runCatching { app.punktgenau.ui.ThemeMode.valueOf(prefs.getString("theme", "LIGHT") ?: "LIGHT") }
            .getOrDefault(app.punktgenau.ui.ThemeMode.LIGHT)
        set(value) { prefs.edit().putString("theme", value.name).apply() }

    val profiles by lazy { ProfileRepository(session, baseUrl) }
}
