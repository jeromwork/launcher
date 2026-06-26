package cryptokit.crypto.api

import cryptokit.crypto.api.values.EscrowBundle

/**
 * Per FR-012 — interface declared in F-CRYPTO; real implementation deferred to a future
 * spec (TBD, number assigned at /speckit.specify time) — see ADR-008 social recovery.
 * [cryptokit.crypto.stubs.StubKeyEscrow] is the sole implementation in F-CRYPTO 1.0.0.
 */
interface KeyEscrow {
    suspend fun export(passphrase: ByteArray): EscrowBundle
    suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray)
}
