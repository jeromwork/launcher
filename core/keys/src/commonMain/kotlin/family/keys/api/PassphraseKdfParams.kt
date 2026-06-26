package family.keys.api

import cryptokit.crypto.api.PasswordHash
import kotlinx.serialization.Serializable

/**
 * Argon2id KDF параметры, embedded в [RecoveryVaultBlob].
 *
 * **Default = interactive profile** (spec 018 FR-030):
 *  • memory = 64 MiB (`PasswordHash.DEFAULT_MEMORY_KIB` = 65 536 KiB)
 *  • iterations (passes) = 3
 *  • parallelism = 1
 *
 * Хранятся **внутри blob'а** (а не как глобальный константы) чтобы при будущем
 * tuning'е (Argon2id ↦ Argon2id с moderate params) старые blob'ы оставались
 * decryptable — caller просто читает params из blob'а и подаёт их в derivation.
 */
@Serializable
data class PassphraseKdfParams(
    val memoryKib: Int = PasswordHash.DEFAULT_MEMORY_KIB,
    val iterations: Int = PasswordHash.DEFAULT_ITERATIONS,
    val parallelism: Int = 1
)
