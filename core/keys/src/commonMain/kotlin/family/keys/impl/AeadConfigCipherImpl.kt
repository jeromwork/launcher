package family.keys.impl

import family.crypto.api.AeadCipher
import family.crypto.api.values.Ciphertext
import family.crypto.exception.CryptoException
import family.keys.api.CipherError
import family.keys.api.ConfigCipher
import family.keys.api.KeyRegistry
import family.keys.api.KeyRegistryError
import family.keys.api.Outcome
import family.keys.api.SealedConfig

/**
 * [ConfigCipher] implementation поверх F-CRYPTO [AeadCipher].
 *
 * **DEK lookup**: всегда `KeyRegistry.getDek("config-cipher-aead-v1")`.
 *
 * **AAD** (FR-020): `"$AAD_PREFIX::$uid::v$schemaVersion"`. Это строковая форма
 * фиксирована, потому что aad embedded в SealedConfig — caller на open'е
 * пересчитывает с известными uid + schemaVersion и сравнивает с blob.aad.
 *
 * **Size limit** (FR-029): plaintext > 256 KB → `CipherError.ConfigTooLarge` ДО
 * encrypt вызова (no wasted CPU).
 *
 * **Schema-version validation** (H-3): при open сначала check'аем
 * `sealed.schemaVersion <= MAX_SUPPORTED` ДО передачи в AEAD, чтобы не
 * атрачивать CPU на decrypt unsupported версии.
 *
 * TODO(future-spec algorithm-migration): при переходе на post-quantum AEAD
 * (например, kyber-768 + chacha20 hybrid) добавить новый algorithm string
 * в [family.keys.api.SealedConfig] + branching по algorithm в open(). Старые
 * v1 blob'ы — readable через legacy path.
 *
 * TODO(future-spec passphrase-change): сейчас нет API для смены passphrase
 * без потери root key. Реализация: unwrap старым passphrase → re-wrap новым →
 * storeVault. Не F-5 scope.
 */
class AeadConfigCipherImpl(
    private val aead: AeadCipher,
    private val keyRegistry: KeyRegistry
) : ConfigCipher {

    override suspend fun seal(configBytes: ByteArray, uid: String): Outcome<SealedConfig, CipherError> {
        if (configBytes.size > MAX_CONFIG_BYTES) {
            return Outcome.Failure(CipherError.ConfigTooLarge)
        }
        require(uid.isNotEmpty()) { "uid must not be empty (FR-020 AAD binding)" }

        val dek = when (val r = keyRegistry.getDek(DEK_NAME)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapKeyRegistryError(r.error))
        }
        val aad = aadFor(uid, SealedConfig.SCHEMA_VERSION)
        return try {
            val ct: Ciphertext = aead.encrypt(configBytes, dek, aad)
            // Расщепляем envelope `nonce(24) || ciphertext || mac(16)` на nonce + (ciphertext+mac).
            val nonce = ct.bytes.copyOfRange(0, NONCE_SIZE)
            val cipherPart = ct.bytes.copyOfRange(NONCE_SIZE, ct.bytes.size)
            Outcome.Success(
                SealedConfig(
                    ciphertext = cipherPart,
                    nonce = nonce,
                    aad = aad
                )
            )
        } catch (t: Throwable) {
            Outcome.Failure(CipherError.InvalidInput(t))
        } finally {
            dek.fill(0)
        }
    }

    override suspend fun open(sealed: SealedConfig, uid: String): Outcome<ByteArray, CipherError> {
        if (sealed.schemaVersion > SealedConfig.SCHEMA_VERSION) {
            return Outcome.Failure(CipherError.AlgorithmUnsupported)
        }
        if (sealed.algorithm != SealedConfig.ALGORITHM_V1) {
            return Outcome.Failure(CipherError.AlgorithmUnsupported)
        }
        if (sealed.nonce.size != NONCE_SIZE) {
            return Outcome.Failure(CipherError.InvalidInput(IllegalArgumentException("nonce size ${sealed.nonce.size} != $NONCE_SIZE")))
        }
        require(uid.isNotEmpty())

        val expectedAad = aadFor(uid, sealed.schemaVersion)
        if (!sealed.aad.contentEquals(expectedAad)) {
            // Identity binding mismatch: try-open under wrong UID. AeadAuthFailed
            // консистентно с tampering response — не разглашаем причину failure'а.
            return Outcome.Failure(CipherError.AeadAuthFailed)
        }

        val dek = when (val r = keyRegistry.getDek(DEK_NAME)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return Outcome.Failure(mapKeyRegistryError(r.error))
        }
        val envelope = sealed.nonce + sealed.ciphertext
        return try {
            val plaintext = aead.decrypt(Ciphertext(envelope), dek, sealed.aad)
            Outcome.Success(plaintext)
        } catch (e: CryptoException.DecryptionFailed) {
            Outcome.Failure(CipherError.AeadAuthFailed)
        } catch (e: CryptoException.MalformedCiphertext) {
            Outcome.Failure(CipherError.InvalidInput(e))
        } catch (t: Throwable) {
            Outcome.Failure(CipherError.InvalidInput(t))
        } finally {
            dek.fill(0)
        }
    }

    private fun aadFor(uid: String, schemaVersion: Int): ByteArray =
        "$AAD_PREFIX::$uid::v$schemaVersion".encodeToByteArray()

    private fun mapKeyRegistryError(error: KeyRegistryError): CipherError = when (error) {
        is KeyRegistryError.NotFound -> CipherError.KeyUnavailable
        is KeyRegistryError.RootKeyUnavailable -> CipherError.KeyUnavailable
        is KeyRegistryError.UnknownDek -> CipherError.AlgorithmUnsupported
        is KeyRegistryError.StorageFailure -> CipherError.InvalidInput(error.cause)
    }

    companion object {
        /** Имя DEK'а для ConfigDocument шифрования. FR-015. */
        const val DEK_NAME: String = "config-cipher-aead-v1"

        /** XChaCha20-Poly1305 nonce size. */
        const val NONCE_SIZE: Int = 24

        /** Spec 018 FR-029 256 KB limit. */
        const val MAX_CONFIG_BYTES: Int = 256 * 1024

        /** Spec 018 FR-020 AAD prefix. */
        const val AAD_PREFIX: String = "f5-config"
    }
}
