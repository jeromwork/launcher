package cryptokit.keys.api.vault

/**
 * Deterministic mock [RecoveryStrategy] for port-contract tests. Extending the sealed class is
 * allowed here because `commonTest` is the same module as the sealed declaration.
 *
 * @param fakeRoot the 32-byte root the strategy will return from [deriveRoot].
 * @param rejectVerify if true, [verifyUnlock] throws [VaultException.RecoveryFailed] — used to
 *   exercise the FR-006b failure-at-unlock path without needing a full passphrase adapter.
 */
class TestRecoveryStrategy(
    private val fakeRoot: ByteArray = ByteArray(32) { it.toByte() },
    private val rejectVerify: Boolean = false,
) : RecoveryStrategy() {
    init {
        require(fakeRoot.size == 32) { "fakeRoot must be 32 bytes, got ${fakeRoot.size}" }
    }

    override suspend fun deriveRoot(): ByteArray = fakeRoot.copyOf()

    override suspend fun verifyUnlock(candidateRoot: ByteArray) {
        if (rejectVerify) throw VaultException.RecoveryFailed("TestRecoveryStrategy rejects")
    }
}
