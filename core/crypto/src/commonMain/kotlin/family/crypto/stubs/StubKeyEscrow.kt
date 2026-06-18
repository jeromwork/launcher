package family.crypto.stubs

import family.crypto.api.KeyEscrow
import family.crypto.api.values.EscrowBundle

/**
 * Interface-only stub per FR-012. Real implementation in a future spec (TBD, number assigned
 * at /speckit.specify time) — see ADR-008 social recovery.
 */
class StubKeyEscrow : KeyEscrow {
    override suspend fun export(passphrase: ByteArray): EscrowBundle =
        throw NotImplementedError(
            "KeyEscrow.export real-impl deferred to future social recovery spec (TBD) — see ADR-008"
        )

    override suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray): Unit =
        throw NotImplementedError(
            "KeyEscrow.restore real-impl deferred to future social recovery spec (TBD) — see ADR-008"
        )
}
