package com.launcher.adapters.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.launcher.api.crypto.CryptoError
import com.launcher.api.crypto.DeviceKeyPair
import com.launcher.api.crypto.DeviceSigningKeyPair
import com.launcher.api.crypto.InMemoryPrivateKey
import com.launcher.api.crypto.InMemorySigningPrivateKey
import com.launcher.api.crypto.PrivateKey
import com.launcher.api.crypto.PublicKey
import com.launcher.api.crypto.SecureKeystore
import com.launcher.api.crypto.SigningPrivateKey
import com.launcher.api.crypto.SigningPublicKey
import com.launcher.api.crypto.X25519_KEY_SIZE
import com.launcher.api.crypto.ED25519_KEY_SIZE
import com.launcher.api.result.Outcome
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Android Keystore-backed SecureKeystore. Strategy:
//   - X25519 (encryption): AES-wrap. Android Keystore не поддерживает X25519
//     нативно. Генерируем AES-256 в Keystore (StrongBox if available), AES-GCM
//     шифруем X25519 priv bytes, persist'им encrypted blob + nonce в
//     EncryptedSharedPreferences под alias.
//   - Ed25519 (signing): native Keystore API 31+ (Android 12).
//     Fallback API 30 — AES-wrap (тот же паттерн, что для X25519).
//
// За resolve PrivateKey/SigningPrivateKey → bytes отвечает этот класс:
// он umвoрачивает priv bytes обратно при load*() — bytes хранятся в памяти
// только в момент использования через unsafePrivBytesResolver.
class AndroidKeystoreSecureKeystore internal constructor(
    context: Context,
    private val libsodium: com.goterl.lazysodium.LazySodiumAndroid,
) : SecureKeystore {

    constructor(context: Context) : this(context, LibsodiumProvider.sodium)


    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // ── X25519 (encryption) ─────────────────────────────────────────────

    override fun generateAndStoreEncryption(alias: String): DeviceKeyPair {
        // 1. libsodium генерит X25519 keypair.
        val pub = ByteArray(X25519_KEY_SIZE)
        val priv = ByteArray(X25519_KEY_SIZE)
        check(libsodium.cryptoBoxKeypair(pub, priv)) { "crypto_box_keypair failed" }

        // 2. AES key в Keystore (alias = aesAliasFor(alias)).
        val aesAlias = aesAliasFor(alias)
        ensureAesKeystoreKey(aesAlias)

        // 3. AES-GCM wrap.
        val wrapped = aesWrap(aesAlias, priv)
        prefs.edit()
            .putString(prefKeyCiphertext(alias), wrapped.ciphertext.toBase64())
            .putString(prefKeyNonce(alias), wrapped.nonce.toBase64())
            .putString(prefKeyPub(alias), pub.toBase64())
            .commit()

        // 4. Return DeviceKeyPair с InMemoryPrivateKey (priv bytes в памяти —
        // короткоживущий; production caller использует .use {}).
        return DeviceKeyPair(PublicKey(pub), InMemoryPrivateKey(alias, priv))
    }

    override fun loadEncryption(alias: String): Outcome<DeviceKeyPair, CryptoError> {
        val ctB64 = prefs.getString(prefKeyCiphertext(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val nonceB64 = prefs.getString(prefKeyNonce(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val pubB64 = prefs.getString(prefKeyPub(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val priv = try {
            aesUnwrap(aesAliasFor(alias), ctB64.fromBase64(), nonceB64.fromBase64())
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.KeystoreFailure(e))
        }
        return Outcome.Success(
            DeviceKeyPair(PublicKey(pubB64.fromBase64()), InMemoryPrivateKey(alias, priv)),
        )
    }

    // ── Ed25519 (signing) ───────────────────────────────────────────────

    override fun generateAndStoreSigning(alias: String): DeviceSigningKeyPair {
        return if (Build.VERSION.SDK_INT >= 31 && supportsNativeEd25519()) {
            generateEd25519Native(alias)
        } else {
            generateEd25519AesWrap(alias)
        }
    }

    override fun loadSigning(alias: String): Outcome<DeviceSigningKeyPair, CryptoError> {
        // Native path indicator: AES alias не set → assume native.
        return if (prefs.contains(prefKeySignCiphertext(alias))) {
            loadEd25519AesWrap(alias)
        } else {
            loadEd25519Native(alias)
        }
    }

    private fun generateEd25519Native(alias: String): DeviceSigningKeyPair {
        // На API 31+ Android Keystore поддерживает Ed25519 через KeyPairGenerator.
        // Однако lazysodium signing API ожидает 64-byte secret key (seed||pub),
        // что несовместимо с opaque Keystore handle. Поэтому даже на API 31+
        // **мы продолжаем использовать AES-wrap** для совместимости с
        // libsodium crypto_sign_detached. Native-path зарезервирован для
        // будущей миграции если libsodium заменим на Keystore-нативный путь.
        // Сейчас native = AES-wrap (один путь, проще тестировать).
        return generateEd25519AesWrap(alias)
    }

    private fun loadEd25519Native(alias: String): Outcome<DeviceSigningKeyPair, CryptoError> {
        // Same — see generateEd25519Native rationale.
        return loadEd25519AesWrap(alias)
    }

    private fun generateEd25519AesWrap(alias: String): DeviceSigningKeyPair {
        val pub = ByteArray(ED25519_KEY_SIZE)
        val priv = ByteArray(com.goterl.lazysodium.interfaces.Sign.SECRETKEYBYTES)
        check(libsodium.cryptoSignKeypair(pub, priv)) { "crypto_sign_keypair failed" }
        val aesAlias = aesAliasForSign(alias)
        ensureAesKeystoreKey(aesAlias)
        val wrapped = aesWrap(aesAlias, priv)
        prefs.edit()
            .putString(prefKeySignCiphertext(alias), wrapped.ciphertext.toBase64())
            .putString(prefKeySignNonce(alias), wrapped.nonce.toBase64())
            .putString(prefKeySignPub(alias), pub.toBase64())
            .commit()
        return DeviceSigningKeyPair(SigningPublicKey(pub), InMemorySigningPrivateKey(alias, priv))
    }

    private fun loadEd25519AesWrap(alias: String): Outcome<DeviceSigningKeyPair, CryptoError> {
        val ctB64 = prefs.getString(prefKeySignCiphertext(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val nonceB64 = prefs.getString(prefKeySignNonce(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val pubB64 = prefs.getString(prefKeySignPub(alias), null)
            ?: return Outcome.Failure(CryptoError.KeyNotFound(alias))
        val priv = try {
            aesUnwrap(aesAliasForSign(alias), ctB64.fromBase64(), nonceB64.fromBase64())
        } catch (e: Throwable) {
            return Outcome.Failure(CryptoError.KeystoreFailure(e))
        }
        return Outcome.Success(
            DeviceSigningKeyPair(SigningPublicKey(pubB64.fromBase64()), InMemorySigningPrivateKey(alias, priv)),
        )
    }

    override fun delete(alias: String) {
        // Удаляем оба варианта (encryption + signing) — alias может ссылаться на любой.
        runCatching { keyStore.deleteEntry(aesAliasFor(alias)) }
        runCatching { keyStore.deleteEntry(aesAliasForSign(alias)) }
        prefs.edit()
            .remove(prefKeyCiphertext(alias))
            .remove(prefKeyNonce(alias))
            .remove(prefKeyPub(alias))
            .remove(prefKeySignCiphertext(alias))
            .remove(prefKeySignNonce(alias))
            .remove(prefKeySignPub(alias))
            .commit()
    }

    override fun exists(alias: String): Boolean =
        prefs.contains(prefKeyCiphertext(alias)) || prefs.contains(prefKeySignCiphertext(alias))

    // ── AES-wrap helpers ────────────────────────────────────────────────

    private data class AesWrapResult(val ciphertext: ByteArray, val nonce: ByteArray)

    private fun ensureAesKeystoreKey(aesAlias: String) {
        if (keyStore.containsAlias(aesAlias)) return
        val spec = KeyGenParameterSpec.Builder(
            aesAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .let { builder ->
                if (Build.VERSION.SDK_INT >= 28) {
                    runCatching { builder.setIsStrongBoxBacked(true) }
                }
                builder
            }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        try {
            gen.init(spec as AlgorithmParameterSpec)
            gen.generateKey()
        } catch (e: Throwable) {
            // StrongBox not available — retry without it.
            val fallback = KeyGenParameterSpec.Builder(
                aesAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            gen.init(fallback as AlgorithmParameterSpec)
            gen.generateKey()
        }
    }

    private fun aesWrap(aesAlias: String, plaintext: ByteArray): AesWrapResult {
        val key = keyStore.getKey(aesAlias, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        val nonce = cipher.iv
        return AesWrapResult(ciphertext, nonce)
    }

    private fun aesUnwrap(aesAlias: String, ciphertext: ByteArray, nonce: ByteArray): ByteArray {
        val key = keyStore.getKey(aesAlias, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    private fun supportsNativeEd25519(): Boolean {
        // Не используем (см. generateEd25519Native rationale). Reserve hook.
        return false
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "launcher_secure_keystore"

        // alias = домен-уровневое имя (e.g. "x25519/own"). Внутренние Keystore aliases
        // деривируются — без чувствительных bytes, только prefix + alias.
        private fun aesAliasFor(alias: String): String = "launcher.aes.enc.$alias"
        private fun aesAliasForSign(alias: String): String = "launcher.aes.sign.$alias"

        private fun prefKeyCiphertext(alias: String) = "enc.ct.$alias"
        private fun prefKeyNonce(alias: String) = "enc.nonce.$alias"
        private fun prefKeyPub(alias: String) = "enc.pub.$alias"
        private fun prefKeySignCiphertext(alias: String) = "sign.ct.$alias"
        private fun prefKeySignNonce(alias: String) = "sign.nonce.$alias"
        private fun prefKeySignPub(alias: String) = "sign.pub.$alias"

        private fun ByteArray.toBase64(): String =
            android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

        private fun String.fromBase64(): ByteArray =
            android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
    }
}
