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
    val teams by lazy { app.punktgenau.data.TeamRepository(session, baseUrl) }
    val profiles by lazy { ProfileRepository(session, baseUrl) }
}
