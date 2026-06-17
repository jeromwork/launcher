package family.crypto.api

import family.crypto.api.values.EscrowBundle

/**
 * Per FR-012 — interface declared in F-CRYPTO; real implementation deferred to spec 017
 * (social recovery per ADR-008). [family.crypto.stubs.StubKeyEscrow] is the sole
 * implementation in F-CRYPTO 1.0.0.
 */
interface KeyEscrow {
    suspend fun export(passphrase: ByteArray): EscrowBundle
    suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray)
}
