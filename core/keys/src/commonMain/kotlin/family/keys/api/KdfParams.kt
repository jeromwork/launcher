package family.keys.api

import kotlinx.serialization.Serializable

/**
 * Argon2id KDF параметры для passphrase derivation (FR-006, data-model.md §5).
 *
 * Хранятся **внутри** [RecoveryKeyBackupBlob] чтобы при будущем tuning'е старые
 * blob'ы оставались decryptable — caller читает params из blob'а и подаёт их в derivation.
 *
 * **MVP defaults** (OWASP 2024 interactive profile, plan.md §Argon2id):
 *  - `algorithm` = `"Argon2id"` (единственный разрешённый в v1; расширение через schemaVersion)
 *  - `iterations` = 3
 *  - `memoryKb` = 65 536 (64 MiB)
 *  - `parallelism` = 1
 *
 * **Validation**: init-block отбрасывает неверные значения немедленно при создании
 * (fail-fast, не lazy). Это гарантирует что domain-объект всегда валиден.
 *
 * @param algorithm Строковый идентификатор KDF-алгоритма. Допустимые значения — `"Argon2id"`.
 * @param iterations Количество passes Argon2id. Минимум 1.
 * @param memoryKb Объём памяти в KiB. Минимум 1024 (1 MiB).
 * @param parallelism Степень параллелизма. Минимум 1.
 */
@Serializable
data class KdfParams(
    val algorithm: String = ALGORITHM_ARGON2ID,
    val iterations: Int = DEFAULT_ITERATIONS,
    val memoryKb: Int = DEFAULT_MEMORY_KB,
    val parallelism: Int = DEFAULT_PARALLELISM,
) {
    init {
        require(algorithm in KNOWN_ALGORITHMS) {
            "KdfParams.algorithm must be one of $KNOWN_ALGORITHMS, got '$algorithm'"
        }
        require(iterations >= 1) {
            "KdfParams.iterations must be >= 1, got $iterations"
        }
        require(memoryKb >= 1024) {
            "KdfParams.memoryKb must be >= 1024, got $memoryKb"
        }
        require(parallelism >= 1) {
            "KdfParams.parallelism must be >= 1, got $parallelism"
        }
    }

    companion object {
        const val ALGORITHM_ARGON2ID: String = "Argon2id"

        /** MVP interactive profile — OWASP 2024. */
        const val DEFAULT_ITERATIONS: Int = 3
        const val DEFAULT_MEMORY_KB: Int = 65_536
        const val DEFAULT_PARALLELISM: Int = 1

        /**
         * Допустимые значения algorithm в v1. Расширение = добавить сюда + bump schemaVersion
         * если семантика меняется, или additive (новый enum value, старые клиенты отвергают
         * `BackupError.UnsupportedAlgorithm`) — без bump.
         */
        internal val KNOWN_ALGORITHMS: Set<String> = setOf(ALGORITHM_ARGON2ID)
    }
}
