package app.punktgenau.data.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicLong

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

/**
 * Cookie-Speicher, der jede echte Änderung sofort verschlüsselt ablegt.
 *
 * - Nur Hosts, die auf [hostSuffix] enden, werden angenommen oder gesendet.
 * - Löschungen (leerer Wert oder abgelaufen) der Anmelde-Cookies (Name beginnt mit `sb-`) werden
 *   ignoriert, damit eine gültige Session nicht überschrieben wird ().
 * - [version] zählt jede Änderung eines Anmelde-Cookie-Werts. Damit fassen parallele Anfragen ihre
 *   Erneuerung zusammen.
 */
class PersistentCookieJar(
    private val storage: SessionStorage,
    private val hostSuffix: String = "mytischtennis.de",
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieJar {

    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = LinkedHashMap<String, Cookie>()
    private val versionCounter = AtomicLong(0)

    val version: Long get() = versionCounter.get()

    init {
        restore()
    }

    @Synchronized
    private fun restore() {
        val bytes = storage.load() ?: return
        try {
            val list = json.decodeFromString<List<StoredCookie>>(bytes.decodeToString())
            for (s in list) {
                val builder = Cookie.Builder().name(s.name).value(s.value).path(s.path).expiresAt(s.expiresAt)
                if (s.hostOnly) builder.hostOnlyDomain(s.domain) else builder.domain(s.domain)
                if (s.secure) builder.secure()
                if (s.httpOnly) builder.httpOnly()
                val c = builder.build()
                cookies[key(c)] = c
            }
        } catch (e: Exception) {
            storage.clear()
            cookies.clear()
        }
    }

    private fun key(c: Cookie) = "${c.name}|${c.domain}|${c.path}"

    private fun isAuthCookie(c: Cookie) = c.name.startsWith("sb-")

    private fun isExpired(c: Cookie) = c.persistent && c.expiresAt <= clock()

    private fun hostAllowed(url: HttpUrl) = url.host == hostSuffix || url.host.endsWith(".$hostSuffix")

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!hostAllowed(url) || cookies.isEmpty()) return
        var changed = false
        var authChanged = false
        for (c in cookies) {
            val k = key(c)
            val isDeletion = c.value.isEmpty() || (c.persistent && c.expiresAt <= clock())
            if (isDeletion) {
                if (isAuthCookie(c)) continue
                if (this.cookies.remove(k) != null) changed = true
            } else {
                val old = this.cookies.put(k, c)
                if (old == null || old.value != c.value || old.expiresAt != c.expiresAt) {
                    changed = true
                    if (isAuthCookie(c) && old?.value != c.value) authChanged = true
                }
            }
        }
        if (authChanged) versionCounter.incrementAndGet()
        if (changed) persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!hostAllowed(url)) return emptyList()
        return cookies.values.filter { !isExpired(it) && it.matches(url) }
    }

    /** Übernimmt Cookies aus dem WebView-Login (Cookie-Header der Website). */
    @Synchronized
    fun importFromHeader(url: HttpUrl, cookieHeader: String) {
        val parsed = cookieHeader.split(';').mapNotNull { part ->
            val p = part.trim()
            if (p.isEmpty()) null else Cookie.parse(url, "$p; Path=/" + if (url.isHttps) "; Secure" else "")
        }
        saveFromResponse(url, parsed)
    }

    @Synchronized
    fun hasAuthCookie(): Boolean = cookies.values.any { isAuthCookie(it) && !isExpired(it) }

    @Synchronized
    fun clear() {
        cookies.clear()
        storage.clear()
        versionCounter.incrementAndGet()
    }

    private fun persist() {
        val list = cookies.values.map {
            StoredCookie(it.name, it.value, it.domain, it.path, it.expiresAt, it.secure, it.httpOnly, it.hostOnly)
        }
        storage.save(json.encodeToString(list).encodeToByteArray())
    }
}
