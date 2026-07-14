package cryptokit.keys.impl.vault

/**
 * Frozen Argon2id parameter set. See FR-006 and Session 7 Q-A — matches Bitwarden default 2023
 * and current OWASP recommendation.
 *
 * Never mutate an existing [Version]; instead ship a new one and bump the wire-format version
 * so old vaults keep decrypting.
 */
data class Argon2Params(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val version: Version,
) {
    enum class Version { V1 }

    companion object {
        val V1 = Argon2Params(
            memoryKiB = 64 * 1024,
            iterations = 3,
            parallelism = 1,
            version = Version.V1,
        )
    }
}
