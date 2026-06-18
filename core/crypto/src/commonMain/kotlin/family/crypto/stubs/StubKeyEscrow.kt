package family.crypto.stubs

import family.crypto.api.KeyEscrow
import family.crypto.api.values.EscrowBundle

/** Interface-only stub per FR-012. Real implementation in spec 017 (ADR-008 social recovery). */
class StubKeyEscrow : KeyEscrow {
    override suspend fun export(passphrase: ByteArray): EscrowBundle =
        throw NotImplementedError(
            "KeyEscrow.export real-impl deferred to spec 017 — see ADR-008"
        )

    override suspend fun restore(bundle: EscrowBundle, passphrase: ByteArray): Unit =
        throw NotImplementedError(
            "KeyEscrow.restore real-impl deferred to spec 017 — see ADR-008"
        )
}
