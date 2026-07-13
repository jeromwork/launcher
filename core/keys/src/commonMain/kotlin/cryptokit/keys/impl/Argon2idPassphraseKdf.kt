package cryptokit.keys.impl

import cryptokit.crypto.api.PasswordHash
import cryptokit.keys.api.KdfParams

/**
 * Wrapper над F-CRYPTO [PasswordHash] (Argon2id) для F-5 recovery flow (T060, FR-021, FR-030).
 *
 * Зачем wrapper:
 *  • Domain separation через info-string (FR-021): salt = `salt`, info bytes
 *    включают UID, чтобы один и тот же passphrase + same salt под разными
 *    identities выводил разные keys.
 *  • Caller обязан CharArray.fill(' ') passphrase сразу после `derive` (FR-013).
 *  • Output ByteArray (32 байта) тоже sensitive — caller обязан `.fill(0)` после
 *    wrap/unwrap (FR-013, G-1 finding).
 *
 * **libsodium API ограничение**: ionspin `PasswordHash.pwhash` принимает только
 * password (String) и salt (UByteArray). НЕТ explicit `info` parameter. Поэтому
 * domain separation мы делаем через **`salt = combinedSalt(salt, uid)`**
 * (HKDF-style mix): создаём deterministic производный salt из (salt || uid bytes)
 * и truncate'им до 16 bytes (libsodium salt size).
 *
 * Это работает потому что Argon2id deterministic относительно salt — для recovery
 * мы пересоздадим тот же combinedSalt (uid известен после Sign-In), и получим
 * тот же derived key.
 *
 * TODO(future-spec algorithm-migration): при переходе на Argon2id v2 или другую
 * memory-hard функцию (например, scrypt или новую libsodium primitive) добавить:
 *  1. New algorithm string в [cryptokit.keys.api.KdfParams.algorithm] (например
 *     "argon2id-v2-xchacha20poly1305-v1");
 *  2. Branching в [cryptokit.keys.impl.RecoveryFlow.performRecovery] по algorithm
 *     поле для backward-compat read;
 *  3. Re-wrap при successful recovery в новый algorithm + повторный uploadBlob.
 */
class Argon2idPassphraseKdf(
    private val passwordHash: PasswordHash
) {

    /**
     * Derive 32-byte wrap key из passphrase + salt + uid.
     *
     * @param passphrase CharArray (caller обнуляет после).
     * @param kdfSalt 32 байта случайного salt из [RecoveryKeyBackupBlob.salt].
     * @param uid Identity stableId для domain separation.
     * @param params Argon2id параметры (memory/iterations/parallelism).
     * @return 32-byte derived key. Caller обнуляет `.fill(0)` после wrap/unwrap (G-1).
     */
    suspend fun derive(
        passphrase: CharArray,
        kdfSalt: ByteArray,
        uid: String,
        params: KdfParams = KdfParams()
    ): ByteArray {
        require(kdfSalt.size >= 16) { "kdfSalt must be >= 16 bytes" }
        require(uid.isNotEmpty()) { "uid must not be empty (FR-021 domain separation)" }
        val combined = combineSalt(kdfSalt, uid)
        return passwordHash.deriveFromPassphrase(
            password = passphrase,
            salt = combined,
            memoryKib = params.memoryKb,
            iterations = params.iterations,
            outputLength = 32
        )
    }

    /**
     * Сочетание kdfSalt + uid в 16-байтовый salt для libsodium.
     *
     * Deterministic: same (kdfSalt, uid) → same combined. Не криптографический
     * mix (XOR + UTF-8 bytes append + truncate), но purpose — domain separation
     * между identities, не criptographic strength (тот покрывается самим
     * Argon2id'ом).
     */
    private fun combineSalt(kdfSalt: ByteArray, uid: String): ByteArray {
        val uidBytes = uid.encodeToByteArray()
        val combined = ByteArray(16)
        // First 8 bytes — kdfSalt.
        kdfSalt.copyInto(combined, 0, 0, minOf(8, kdfSalt.size))
        // Next 8 bytes — XOR of remaining kdfSalt with cycled uidBytes.
        for (i in 8 until 16) {
            val saltByte = if (i < kdfSalt.size) kdfSalt[i].toInt() else 0
            val uidByte = uidBytes[(i - 8) % uidBytes.size].toInt()
            combined[i] = (saltByte xor uidByte).toByte()
        }
        return combined
    }
}
