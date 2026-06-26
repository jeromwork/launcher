package cryptokit.crypto.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import cryptokit.crypto.api.values.KeyBlob
import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.exception.CryptoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android actual — wrap-pattern per research.md §R3 + contracts/key-blob-v1.md.
 *
 * Layer 1 (TEE): AES-256-GCM key in Android Keystore under [WRAP_KEY_ALIAS].
 * Layer 2 (file): wrapped private key bytes serialized as [KeyBlob] JSON under
 * `<filesDir>/keys/${keyId.raw}.blob`.
 *
 * StrongBox-backed where available (Pixel Titan / Samsung Knox), falls back to TEE.
 * `setUserAuthenticationRequired(false)` — admin/identity keys MUST NOT block on
 * biometric/PIN per spec.md Edge Cases (no biometric requirement for the F-CRYPTO baseline).
 *
 * TODO(pre-release-audit): TEE attestation hard-fail — на init проверять
 * `KeyInfo.isInsideSecureHardware`. Если false на production устройстве — для
 * paid features возвращать KeystoreUnavailable; для local-mode — log warning.
 * Срок: перед платным релизом. См. docs/dev/crypto-review.md §A4.
 *
 * TODO(pre-release-audit): real-device verification на Pixel (StrongBox) и
 * Samsung Galaxy A-series (Knox) — owner покупает Pixel б/у в течение 3-4
 * месяцев. См. docs/dev/crypto-review.md §A4.
 */
actual class SecureKeyStore actual constructor(context: KeyStoreContext) {

    private val androidContext: Context = context.androidContext
    private val keysDir: File by lazy { File(androidContext.filesDir, KEYS_DIR).apply { mkdirs() } }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual suspend fun store(keyId: KeyId, secret: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val wrapKey = ensureWrapKey()
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
            val wrapped = cipher.doFinal(secret)
            val iv = cipher.iv
            val blob = KeyBlob(
                algorithm = inferAlgorithm(secret),
                createdAt = Clock.System.now(),
                wrappedKey = wrapped,
                iv = iv,
                wrapKeyAlias = WRAP_KEY_ALIAS
            )
            val text = json.encodeToString(KeyBlob.serializer(), blob)
            keyFile(keyId).writeBytes(text.encodeToByteArray())
        } catch (e: KeyStoreException) {
            throw CryptoException.KeystoreUnavailable("Android Keystore not available", e)
        } catch (e: UnrecoverableKeyException) {
            throw CryptoException.KeystoreInvalidated("Wrap key alias invalidated", e)
        }
    }

    actual suspend fun load(keyId: KeyId): ByteArray? = withContext(Dispatchers.IO) {
        val file = keyFile(keyId)
        if (!file.exists()) return@withContext null
        val text = file.readBytes().decodeToString()
        val blob = try {
            json.decodeFromString(KeyBlob.serializer(), text)
        } catch (e: Exception) {
            throw CryptoException.KeyBlobDeserializationFailed(
                "Cannot parse KeyBlob for ${keyId.raw}", e
            )
        }
        if (blob.schemaVersion > KeyBlob.CURRENT_SCHEMA_VERSION) {
            throw CryptoException.UnsupportedSchemaVersion(
                found = blob.schemaVersion,
                known = KeyBlob.CURRENT_SCHEMA_VERSION
            )
        }
        val wrapKey = loadWrapKeyOrThrow(blob.wrapKeyAlias)
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        try {
            cipher.doFinal(blob.wrappedKey)
        } catch (e: Exception) {
            throw CryptoException.KeystoreInvalidated(
                "Unwrap failed for ${keyId.raw} — wrap key may have been invalidated", e
            )
        }
    }

    actual suspend fun delete(keyId: KeyId) = withContext(Dispatchers.IO) {
        keyFile(keyId).delete()
        Unit
    }

    private fun keyFile(keyId: KeyId): File = File(keysDir, "${keyId.raw}.blob")

    private fun ensureWrapKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(WRAP_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        // First attempt StrongBox-backed key (Pixel Titan / Samsung Knox). On devices
        // without StrongBox (older phones, emulators, low-tier OEMs) generateKey throws
        // StrongBoxUnavailableException — fall back to TEE-backed key without StrongBox.
        return try {
            generateWrapKey(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generateWrapKey(strongBox = false)
        }
    }

    private fun generateWrapKey(strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            WRAP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(WRAP_KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun loadWrapKeyOrThrow(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = ks.getKey(alias, null) as? SecretKey
            ?: throw CryptoException.KeystoreInvalidated(
                "Wrap key alias '$alias' not found in Android Keystore (app reinstall? data clear?)"
            )
        return key
    }

    private fun inferAlgorithm(secret: ByteArray): String = when (secret.size) {
        32 -> "X25519"
        64 -> "Ed25519"
        else -> "RAW"
    }

    companion object {
        const val WRAP_KEY_ALIAS = "family-crypto-wrap-key-v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val WRAP_KEY_SIZE_BITS = 256
        const val KEYS_DIR = "keys"
    }
}

actual class KeyStoreContext(val androidContext: Context)
