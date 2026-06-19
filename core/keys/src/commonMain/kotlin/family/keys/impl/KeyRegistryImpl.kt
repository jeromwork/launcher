package family.keys.impl

import family.crypto.api.AeadCipher
import family.crypto.api.SecureKeyStore
import family.crypto.api.values.Ciphertext
import family.crypto.api.values.KeyId
import family.crypto.exception.CryptoException
import family.keys.api.KeyRegistry
import family.keys.api.KeyRegistryError
import family.keys.api.Outcome
import family.keys.api.RootKey

/**
 * [KeyRegistry] implementation.
 *
 * **Storage layout** в [SecureKeyStore]:
 *  • Один [KeyId] per (identity, dek-name) pair: `keys-dek::{uid}::{name}` — это
 *    raw bytes сериализованного [WrappedDek] (без Base64; binary).
 *
 * **Identity partitioning** (FR-031): namespace key = `uid` параметр конструктора.
 * Switch identity → new `KeyRegistryImpl(uid2, ...)` экземпляр; старая instance
 * остаётся валидной для uid1, ключи uid2 не пересекаются по KeyId namespace.
 *
 * **Wrap pattern**: DEK plaintext → AEAD encrypt под root key (с AAD = name) →
 * Ciphertext envelope сохранён в SecureKeyStore. Это **доп. layer wrap'а** на
 * случай если SecureKeyStore сам по себе кому-то доступен — без root key DEK
 * не вернётся.
 *
 * @param uid идентификатор identity для partitioning'а.
 * @param rootKeyProvider лazy provider root key — вызывается на каждую seal/unseal
 *   операцию. Если возвращает null — `RootKeyUnavailable`.
 * @param secureKeyStore underlying persistent storage (Android Keystore / JVM in-mem).
 * @param aead AEAD primitive для DEK wrap/unwrap.
 */
class KeyRegistryImpl(
    private val uid: String,
    private val rootKeyProvider: suspend () -> RootKey?,
    private val secureKeyStore: SecureKeyStore,
    private val aead: AeadCipher
) : KeyRegistry {

    init {
        require(uid.isNotEmpty()) { "uid must not be empty (H-4 cross-UID alias formation guard)" }
    }

    override suspend fun registerDek(name: String, dekMaterial: ByteArray): Outcome<Unit, KeyRegistryError> {
        require(dekMaterial.size == DEK_SIZE) { "DEK must be exactly $DEK_SIZE bytes, got ${dekMaterial.size}" }
        val root = rootKeyProvider() ?: return Outcome.Failure(KeyRegistryError.RootKeyUnavailable)
        return try {
            val ct: Ciphertext = aead.encrypt(
                plaintext = dekMaterial,
                key = root.bytes,
                aad = aadForDek(name)
            )
            secureKeyStore.store(keyIdFor(name), ct.bytes)
            Outcome.Success(Unit)
        } catch (t: Throwable) {
            Outcome.Failure(KeyRegistryError.StorageFailure(t))
        }
    }

    override suspend fun getDek(name: String): Outcome<ByteArray, KeyRegistryError> {
        val envelope = try {
            secureKeyStore.load(keyIdFor(name))
        } catch (t: Throwable) {
            return Outcome.Failure(KeyRegistryError.StorageFailure(t))
        } ?: return Outcome.Failure(KeyRegistryError.NotFound)

        val root = rootKeyProvider() ?: return Outcome.Failure(KeyRegistryError.RootKeyUnavailable)
        return try {
            val dek = aead.decrypt(Ciphertext(envelope), root.bytes, aadForDek(name))
            Outcome.Success(dek)
        } catch (e: CryptoException) {
            Outcome.Failure(KeyRegistryError.UnknownDek)
        } catch (t: Throwable) {
            Outcome.Failure(KeyRegistryError.StorageFailure(t))
        }
    }

    override suspend fun hasDek(name: String): Boolean = try {
        secureKeyStore.load(keyIdFor(name)) != null
    } catch (t: Throwable) {
        false
    }

    private fun keyIdFor(name: String): KeyId =
        KeyId("config-dek-${normalizeUid(uid)}-$name")

    private fun normalizeUid(uid: String): String =
        uid.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }.joinToString("")
            .replace(Regex("-+"), "-").trim('-')

    private fun aadForDek(name: String): ByteArray =
        ("$AAD_PREFIX::$uid::$name").encodeToByteArray()

    companion object {
        const val DEK_SIZE: Int = 32
        const val NAMESPACE_PREFIX: String = "config-dek"
        const val AAD_PREFIX: String = "f5-dek-v1"
    }
}

