package app.punktgenau.data.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class SessionState {
    /** Es gibt kein Anmelde-Cookie. */
    LoggedOut,

    /** Ein Anmelde-Cookie ist vorhanden und wurde zuletzt akzeptiert (oder noch nicht abgelehnt). */
    Active,

    /** Der Server hat die Session trotz stillem Erneuerungsversuch abgelehnt. */
    NeedsLogin,

    /** Der Server verlangt eine Captcha-Prüfung (Umleitung auf /verify). */
    CaptchaRequired,
}

data class FetchResult(val code: Int, val finalUrl: String, val body: String)

/** Erkennt "nicht angemeldet" in Antworten (). */
object AuthDetector {
    private val isAuthenticatedFalse = Regex("\"isAuthenticated\"\\s*:\\s*false")

    fun isUnauthenticated(body: String): Boolean {
        if (body.contains("status=unauthenticated")) return true
        if (isAuthenticatedFalse.containsMatchIn(body)) return true
        if (body.length < SHORT_BODY && body.contains("\"error\"")) {
            if (body.contains("PT403")) return true
            val lower = body.lowercase()
            if (lower.contains("not authorized") || lower.contains("unauthorized") || body.contains("NUID")) return true
        }
        return false
    }

    fun isCaptcha(finalUrl: String) = finalUrl.contains("/verify")

    private const val SHORT_BODY = 2000
}

fun buildHttpClient(
    jar: PersistentCookieJar,
    userAgent: String = DEFAULT_USER_AGENT,
): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(jar)
    .connectTimeout(50, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(60, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept-Language", "de,en-US;q=0.7,en;q=0.3")
                .header("Cache-Control", "no-cache")
                .build(),
        )
    }
    .build()

const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 punktgenau/0.1"

/**
 * Führt Anfragen mit der gespeicherten Session aus und kümmert sich um Erneuerung und Zustand.
 *
 * Ablauf bei "nicht angemeldet": Der Server hat das Cookie womöglich schon in dieser Antwort erneuert.
 * Sonst folgt genau ein stiller Erneuerungsversuch ([probePath]), danach genau ein Wiederholen der
 * ursprünglichen Anfrage. Gelingt das nicht, wechselt der Zustand auf [SessionState.NeedsLogin].
 * Parallele Anfragen teilen sich einen einzigen Erneuerungsversuch.
 */
class SessionManager(
    private val jar: PersistentCookieJar,
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val probePath: String = "/community",
) {
    private val _state = MutableStateFlow(if (jar.hasAuthCookie()) SessionState.Active else SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val refreshLock = Mutex()
    private var lastProbeVersion = -1L

    suspend fun fetch(request: Request): FetchResult {
        val versionBefore = jar.version
        var result = execute(request)
        if (AuthDetector.isCaptcha(result.finalUrl)) {
            _state.value = SessionState.CaptchaRequired
            return result
        }
        if (AuthDetector.isUnauthenticated(result.body)) {
            refreshLock.withLock {
                if (jar.version == versionBefore && lastProbeVersion != versionBefore) {
                    lastProbeVersion = versionBefore
                    runCatching { execute(Request.Builder().url(baseUrl.resolve(probePath)!!).build()) }
                }
            }
            result = execute(request)
            if (AuthDetector.isCaptcha(result.finalUrl)) {
                _state.value = SessionState.CaptchaRequired
                return result
            }
            if (AuthDetector.isUnauthenticated(result.body)) {
                _state.value = SessionState.NeedsLogin
                return result
            }
        }
        _state.value = if (jar.hasAuthCookie()) SessionState.Active else SessionState.LoggedOut
        return result
    }

    /** Nach dem WebView-Login: Cookie-Header der Website übernehmen. */
    fun onLoginCompleted(cookieHeader: String) {
        jar.importFromHeader(baseUrl, cookieHeader)
        _state.value = if (jar.hasAuthCookie()) SessionState.Active else SessionState.LoggedOut
    }

    fun logout() {
        jar.clear()
        _state.value = SessionState.LoggedOut
    }

    private suspend fun execute(request: Request): FetchResult = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            FetchResult(response.code, response.request.url.toString(), response.body.string())
        }
    }
}
