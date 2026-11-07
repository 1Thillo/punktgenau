package app.punktgenau.data.session

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionManagerTest {

    private lateinit var server: MockWebServer
    private val storage = InMemorySessionStorage()
    private val hits = HashMap<String, AtomicInteger>()

    private fun hits(path: String) = synchronized(hits) { hits.getOrPut(path) { AtomicInteger() } }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.close()
    }

    private fun newJar(storage: SessionStorage = this.storage) = PersistentCookieJar(storage, hostSuffix = server.hostName)

    private fun manager(jar: PersistentCookieJar) =
        SessionManager(jar, buildHttpClient(jar), server.url("/"))

    private fun get(path: String) = Request.Builder().url(server.url(path)).build()

    private fun setDispatcher(block: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                hits(request.url.encodedPath).incrementAndGet()
                return block(request)
            }
        }
    }

    private fun ok(body: String = "ok", vararg setCookie: String): MockResponse {
        val b = MockResponse.Builder().code(200).body(body)
        setCookie.forEach { b.addHeader("Set-Cookie", it) }
        return b.build()
    }

    private val unauth get() = ok("<html>status=unauthenticated</html>")

    private fun RecordedRequest.cookie(): String = headers["Cookie"].orEmpty()

    // (1) Cookies überstehen einen Neustart --------------------------------------

    @Test fun cookiesSurviveRestart() = runBlocking {
        setDispatcher { ok("angemeldet", "sb-10-auth-token=abc123; Path=/; Max-Age=3600") }
        val first = manager(newJar())
        first.fetch(get("/login"))
        assertEquals(SessionState.Active, first.state.value)

        // "Neustart": neuer Jar aus demselben Speicher
        val restartedJar = newJar()
        assertTrue(restartedJar.hasAuthCookie())
        val second = manager(restartedJar)
        assertEquals(SessionState.Active, second.state.value)

        var sent = ""
        setDispatcher { sent = it.cookie(); ok() }
        second.fetch(get("/data"))
        assertTrue(sent.contains("sb-10-auth-token=abc123"))
    }

    @Test fun sitzungscookieOhneAblaufUeberlebtNeustart() = runBlocking {
        setDispatcher { ok("x", "sb-10-auth-token=sess; Path=/") }
        manager(newJar()).fetch(get("/login"))
        assertTrue(newJar().hasAuthCookie())
    }

    // (2) Abgelaufene Session wird still erneuert ------------------------------

    @Test fun abgelaufeneSession_stillErneuert_ohneNutzeraktion() = runBlocking {
        val jar = newJar()
        val m = manager(jar)
        m.onLoginCompleted("sb-10-auth-token=alt")
        setDispatcher { r ->
            when {
                r.cookie().contains("sb-10-auth-token=neu") -> ok("daten")
                r.url.encodedPath == "/community" -> ok("start", "sb-10-auth-token=neu; Path=/; Max-Age=3600")
                else -> unauth
            }
        }
        val result = m.fetch(get("/api/ttr/player/NU1"))
        assertEquals("daten", result.body)
        assertEquals(SessionState.Active, m.state.value)
        assertEquals(1, hits("/community").get())
        assertTrue(newJar().hasAuthCookie())
    }

    @Test fun serverErneuertCookieSchonInDerAblehnendenAntwort_keinExtraAufruf() = runBlocking {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=alt")
        setDispatcher { r ->
            if (r.cookie().contains("sb-10-auth-token=neu")) ok("daten")
            else ok("<html>status=unauthenticated</html>", "sb-10-auth-token=neu; Path=/; Max-Age=3600")
        }
        assertEquals("daten", m.fetch(get("/x")).body)
        assertEquals(0, hits("/community").get())
        assertEquals(SessionState.Active, m.state.value)
    }

    // (3) Dauerhafte Ablehnung -------------------------------------------------

    @Test fun dauerhaftAbgelehnt_wirdNeedsLogin_miteinemErneuerungsversuch() = runBlocking {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=tot")
        setDispatcher { unauth }
        val result = m.fetch(get("/api/ttr/player/NU1"))
        assertTrue(AuthDetector.isUnauthenticated(result.body))
        assertEquals(SessionState.NeedsLogin, m.state.value)
        assertEquals(2, hits("/api/ttr/player/NU1").get()) // einmal wiederholt, nicht öfter
        assertEquals(1, hits("/community").get())
    }

    @Test fun jsonFehlerPT403_wirdAlsNichtAngemeldetErkannt() = runBlocking {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=tot")
        setDispatcher { ok("""{"error":{"code":"PT403","message":"not authorized"}}""") }
        m.fetch(get("/api/ttr/history/NU1"))
        assertEquals(SessionState.NeedsLogin, m.state.value)
    }

    // (4) Löschen mit leerem Wert überschreibt nichts -----------------------------

    @Test fun loeschungMitLeeremWertUndMaxAgeNull_wirdIgnoriert() = runBlocking {
        val jar = newJar()
        val m = manager(jar)
        m.onLoginCompleted("sb-10-auth-token=gueltig")
        setDispatcher { ok("x", "sb-10-auth-token=; Path=/; Max-Age=0", "sb-10-auth-token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT") }
        m.fetch(get("/seite"))
        assertTrue(jar.hasAuthCookie())
        assertTrue(newJar().hasAuthCookie()) // auch im Speicher
        var sent = ""
        setDispatcher { sent = it.cookie(); ok() }
        m.fetch(get("/seite2"))
        assertTrue(sent.contains("sb-10-auth-token=gueltig"))
    }

    @Test fun andereCookiesDuerfenGeloeschtWerden() = runBlocking {
        val jar = newJar()
        val m = manager(jar)
        setDispatcher { ok("x", "tracking=1; Path=/") }
        m.fetch(get("/a"))
        setDispatcher { ok("x", "tracking=; Path=/; Max-Age=0") }
        m.fetch(get("/b"))
        var sent = ""
        setDispatcher { sent = it.cookie(); ok() }
        m.fetch(get("/c"))
        assertFalse(sent.contains("tracking"))
    }

    // (5) Parallele Anfragen -----------------------------------------------------

    @Test fun parallelAnfragen_loesenNurEinenErneuerungsversuchAus() = runBlocking {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=alt")
        setDispatcher { r ->
            when {
                r.cookie().contains("sb-10-auth-token=neu") -> ok("daten")
                r.url.encodedPath == "/community" -> {
                    Thread.sleep(150)
                    ok("start", "sb-10-auth-token=neu; Path=/; Max-Age=3600")
                }
                else -> unauth
            }
        }
        val results = (1..6).map { i -> async { m.fetch(get("/api/$i")) } }.awaitAll()
        assertTrue(results.all { it.body == "daten" })
        assertEquals(1, hits("/community").get())
        assertEquals(SessionState.Active, m.state.value)
    }

    // (6) Captcha-Umleitung ---------------------------------------------------------

    @Test fun captchaUmleitung_setztZustand() = runBlocking {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=x")
        setDispatcher { r ->
            if (r.url.encodedPath == "/verify") ok("captcha")
            else MockResponse.Builder().code(302).addHeader("Location", "/verify").build()
        }
        val result = m.fetch(get("/api/x"))
        assertTrue(result.finalUrl.contains("/verify"))
        assertEquals(SessionState.CaptchaRequired, m.state.value)
        assertEquals(0, hits("/community").get())
    }

    // (7) Beschädigter Block ---------------------------------------------------------

    @Test fun beschaedigterBlock_wirdVerworfen_keinAbsturz() {
        val bad = InMemorySessionStorage().apply { data = "kein json {{{".encodeToByteArray() }
        val jar = PersistentCookieJar(bad, hostSuffix = server.hostName)
        assertFalse(jar.hasAuthCookie())
        assertEquals(null, bad.data)
        assertEquals(SessionState.LoggedOut, manager(jar).state.value)
    }

    // Anmeldung / Abmeldung ---------------------------------------------------------------

    @Test fun abmelden_loeschtAlles() {
        val m = manager(newJar())
        m.onLoginCompleted("sb-10-auth-token=abc; andere=1")
        assertEquals(SessionState.Active, m.state.value)
        m.logout()
        assertEquals(SessionState.LoggedOut, m.state.value)
        assertEquals(null, storage.data)
        assertFalse(newJar().hasAuthCookie())
    }

    @Test fun cookieWirdNurFuerDieErlaubteDomaineGesendet() = runBlocking {
        val jar = PersistentCookieJar(storage, hostSuffix = "mytischtennis.de")
        val m = SessionManager(jar, buildHttpClient(jar), server.url("/"))
        setDispatcher { ok("x", "sb-10-auth-token=abc; Path=/") }
        m.fetch(get("/a"))
        assertFalse(jar.hasAuthCookie()) // fremder Host wird nicht gespeichert
    }

    // Erkennung ------------------------------------------------------------------------

    @Test fun erkennung() {
        assertTrue(AuthDetector.isUnauthenticated("x status=unauthenticated y"))
        assertTrue(AuthDetector.isUnauthenticated("""{"a":1,"isAuthenticated": false}"""))
        assertFalse(AuthDetector.isUnauthenticated("""{"isAuthenticated":true}"""))
        assertFalse(AuthDetector.isUnauthenticated("""{"ttr":1500}"""))
        assertTrue(AuthDetector.isCaptcha("https://www.mytischtennis.de/verify?x=1"))
        assertFalse(AuthDetector.isCaptcha("https://www.mytischtennis.de/community"))
    }
}
