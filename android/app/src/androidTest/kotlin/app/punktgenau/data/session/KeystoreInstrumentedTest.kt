package app.punktgenau.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Läuft auf dem Gerät mit dem echten Android Keystore. Benutzt eine Datei im Cache, nicht die echte Session.
 * Fängt Fehler, die die JVM nicht kennt (z. B. "Caller-provided IV not permitted").
 */
@RunWith(AndroidJUnit4::class)
class KeystoreInstrumentedTest {

    private lateinit var file: File

    @Before fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(ctx.cacheDir, "keystore_test_session.bin").also { it.delete() }
    }

    @After fun tearDown() {
        file.delete()
    }

    @Test fun verschluesselnUndEntschluesseln() {
        val cipher = KeystoreKey.cipher()
        val plain = "geheim 123".encodeToByteArray()
        val blob = cipher.encrypt(plain)
        assertArrayEquals(plain, cipher.decrypt(blob))
    }

    @Test fun jedeVerschluesselungHatEinenAnderenBlock() {
        val cipher = KeystoreKey.cipher()
        assertNotEquals(cipher.encrypt(byteArrayOf(1, 2, 3)).toList(), cipher.encrypt(byteArrayOf(1, 2, 3)).toList())
    }

    @Test fun dateiEnthaeltKeinenKlartext() {
        EncryptedFileSessionStorage(file, KeystoreKey.cipher()).save("sb-10-auth-token=abc".encodeToByteArray())
        assertTrue(file.exists())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("auth-token"))
    }

    @Test fun manipulierterBlockWirdVerworfen() {
        val storage = EncryptedFileSessionStorage(file, KeystoreKey.cipher())
        storage.save("geheim".encodeToByteArray())
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(storage.load())
    }

    @Test fun cookieJarUeberlebtNeustartMitEchtemKeystore() {
        val url = "https://www.mytischtennis.de/".toHttpUrl()
        val jar1 = PersistentCookieJar(EncryptedFileSessionStorage(file, KeystoreKey.cipher()))
        jar1.importFromHeader(url, "sb-10-auth-token=wert1; x=2")
        assertTrue(jar1.hasAuthCookie())
        // "Neustart": neuer Jar, neuer Speicher, neue Chiffre, gleiche Datei
        val jar2 = PersistentCookieJar(EncryptedFileSessionStorage(file, KeystoreKey.cipher()))
        assertTrue(jar2.hasAuthCookie())
        assertTrue(jar2.loadForRequest(url).any { it.name == "sb-10-auth-token" && it.value == "wert1" })
    }
}
