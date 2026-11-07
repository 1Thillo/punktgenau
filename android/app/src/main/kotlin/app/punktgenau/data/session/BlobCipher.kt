package app.punktgenau.data.session

import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verschlüsselt kleine Datenblöcke. Fehler beim Entschlüsseln werfen [GeneralSecurityException]. */
interface BlobCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/** AES-256-GCM. Ablage: 12 Byte IV, danach Chiffretext mit Tag. Den Schlüssel liefert [keyProvider]. */
class AesGcmBlobCipher(private val keyProvider: () -> SecretKey) : BlobCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        // Der Android Keystore verbietet eigene IVs beim Verschlüsseln: den IV erzeugt die Chiffre selbst.
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Unerwartete IV-Länge" }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= IV_LENGTH) throw GeneralSecurityException("Datenblock zu kurz")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH),
        )
        return cipher.doFinal(blob, IV_LENGTH, blob.size - IV_LENGTH)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

/** Schlüssel im Android Keystore: nicht exportierbar, wird beim ersten Zugriff angelegt. */
object KeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "punktgenau_session_key"

    @Synchronized
    fun getOrCreate(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", PROVIDER)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun cipher(): BlobCipher = AesGcmBlobCipher { getOrCreate() }
}
