package app.punktgenau.data.session

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Ablage des verschlüsselten Session-Blocks. Fehler führen nie zum Absturz, sondern zu "nichts gespeichert". */
interface SessionStorage {
    fun load(): ByteArray?
    fun save(plain: ByteArray)
    fun clear()
}

/**
 * Legt den Block verschlüsselt in einer Datei ab (atomar über Temp-Datei und Umbenennen).
 * Ein beschädigter oder nicht entschlüsselbarer Block wird verworfen.
 */
class EncryptedFileSessionStorage(
    private val file: File,
    private val cipher: BlobCipher,
) : SessionStorage {

    @Synchronized
    override fun load(): ByteArray? {
        if (!file.exists()) return null
        return try {
            cipher.decrypt(file.readBytes())
        } catch (e: Exception) {
            // Beschädigt oder Schlüssel weg: verwerfen, der Login wird angeboten.
            file.delete()
            null
        }
    }

    @Synchronized
    override fun save(plain: ByteArray) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(cipher.encrypt(plain))
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            // Speichern fehlgeschlagen: kein Absturz, nie im Klartext ablegen. Die Session bleibt im Speicher.
        }
    }

    @Synchronized
    override fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }
}

/** Für Tests und Vorschauen. */
class InMemorySessionStorage : SessionStorage {
    @Volatile
    var data: ByteArray? = null

    override fun load(): ByteArray? = data
    override fun save(plain: ByteArray) {
        data = plain.copyOf()
    }

    override fun clear() {
        data = null
    }
}
