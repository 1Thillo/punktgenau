package app.punktgenau.data.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class EncryptedStorageTest {

    @get:Rule val tmp = TemporaryFolder()

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmBlobCipher { key }

    @Test fun rundreise() {
        val file = tmp.root.resolve("session.bin")
        val storage = EncryptedFileSessionStorage(file, cipher)
        storage.save("geheim".encodeToByteArray())
        assertArrayEquals("geheim".encodeToByteArray(), EncryptedFileSessionStorage(file, cipher).load())
    }

    @Test fun dateiEnthaeltKeinenKlartext() {
        val file = tmp.root.resolve("session.bin")
        EncryptedFileSessionStorage(file, cipher).save("sb-10-auth-token=abc".encodeToByteArray())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("auth-token"))
    }

    @Test fun jedeVerschluesselungHatEinenNeuenIv() {
        val a = cipher.encrypt("x".encodeToByteArray())
        val b = cipher.encrypt("x".encodeToByteArray())
        assertNotEquals(a.toList(), b.toList())
    }

    @Test fun manipulierterBlock_wirdVerworfen() {
        val file = tmp.root.resolve("session.bin")
        val storage = EncryptedFileSessionStorage(file, cipher)
        storage.save("geheim".encodeToByteArray())
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertNull(storage.load())
        assertFalse(file.exists())
    }

    @Test fun andererSchluessel_wirdVerworfen() {
        val file = tmp.root.resolve("session.bin")
        EncryptedFileSessionStorage(file, cipher).save("geheim".encodeToByteArray())
        val other = AesGcmBlobCipher { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        assertNull(EncryptedFileSessionStorage(file, other).load())
    }

    @Test fun zuKurzerBlock_wirdVerworfen() {
        val file = tmp.root.resolve("session.bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(EncryptedFileSessionStorage(file, cipher).load())
    }

    @Test fun cookieJarMitVerschluesseltemSpeicher_ueberlebtNeustart() {
        val file = tmp.root.resolve("session.bin")
        val jar1 = PersistentCookieJar(EncryptedFileSessionStorage(file, cipher), hostSuffix = "mytischtennis.de")
        jar1.importFromHeader(okhttp3.HttpUrl.Builder().scheme("https").host("www.mytischtennis.de").build(), "sb-10-auth-token=wert1; x=2")
        val jar2 = PersistentCookieJar(EncryptedFileSessionStorage(file, cipher), hostSuffix = "mytischtennis.de")
        assertEquals(true, jar2.hasAuthCookie())
    }
}
