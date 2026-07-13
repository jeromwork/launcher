package cryptokit.keys.impl

import cryptokit.keys.api.AuthIdentity
import cryptokit.keys.api.Outcome
import cryptokit.keys.api.RecoveryError
import cryptokit.keys.api.RootKey
import cryptokit.keys.api.RootKeyError

/**
 * Adapter wiring [RecoveryFlow.performRecovery] into [RootKeyManagerImpl.recover]
 * via the [RecoveryDelegate] indirection.
 *
 * Maps the recovery domain errors ([RecoveryError]) onto the root-key domain
 * errors ([RootKeyError]) so call sites that depend only on
 * [cryptokit.keys.api.RootKeyManager] keep a single error vocabulary.
 *
 * **Mapping** (FR-027 / spec §Domain errors):
 *  - [RecoveryError.WrongPassphrase]     → [RootKeyError.WrongPassphrase]
 *  - [RecoveryError.TooManyAttempts]     → [RootKeyError.RecoveryRequired]
 *                                          (caller's UI moves user to Fallback)
 *  - [RecoveryError.NoVaultPresent]      → [RootKeyError.RecoveryRequired]
 *                                          (no blob → caller should run setup)
 *  - [RecoveryError.MalformedVault]      → [RootKeyError.CorruptedBlob] (cause=null)
 *  - [RecoveryError.Cancelled]           → [RootKeyError.NoIdentity]
 *                                          (treat user-cancel like absence of
 *                                          identity — no derived state)
 *
 * The success path is straightforward — both surfaces speak [RootKey].
 *
 * **CharArray ownership** (FR-013): the passphrase is forwarded into
 * [RecoveryFlow.performRecovery], which owns the wipe in its `finally` block.
 * This adapter does not buffer or copy the array.
 */
class RecoveryFlowDelegate(
    private val recoveryFlow: RecoveryFlow
) : RecoveryDelegate {

    override suspend fun recover(
        identity: AuthIdentity,
        passphrase: CharArray
    ): Outcome<RootKey, RootKeyError> {
        return when (val result = recoveryFlow.performRecovery(identity)) {
            is Outcome.Success -> Outcome.Success(result.value)
            is Outcome.Failure -> Outcome.Failure(mapError(result.error))
        }
    }

    private fun mapError(err: RecoveryError): RootKeyError = when (err) {
        RecoveryError.WrongPassphrase -> RootKeyError.WrongPassphrase
        RecoveryError.TooManyAttempts -> RootKeyError.RecoveryRequired
        RecoveryError.NoVaultPresent -> RootKeyError.RecoveryRequired
        RecoveryError.MalformedVault -> RootKeyError.CorruptedBlob()
        RecoveryError.Cancelled -> RootKeyError.NoIdentity
    }
}
