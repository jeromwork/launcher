package cryptokit.keys.vault

import cryptokit.keys.api.vault.Purpose
import cryptokit.keys.api.vault.VaultException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compile-time proof that the sealed [VaultException] hierarchy is exhaustive — omitting a
 * branch here would break the build. If a new variant is added, this test refuses to compile
 * until the `when` is updated.
 */
class VaultExceptionExhaustivenessTest {

    @Test
    fun `exhaustive when on VaultException without else`() {
        val samples: List<VaultException> = listOf(
            VaultException.VaultLocked(),
            VaultException.WrongPurpose(Purpose.CONFIG, 0x0002),
            VaultException.TamperDetected(),
            VaultException.UnsupportedFormatVersion(0x99),
            VaultException.NoRootKey(),
            VaultException.HardwareBackedKeystoreUnavailable("test"),
            VaultException.RecoveryFailed(),
        )
        val labels: List<String> = samples.map { ex ->
            when (ex) {
                is VaultException.VaultLocked -> "locked"
                is VaultException.WrongPurpose -> "wrong-purpose"
                is VaultException.TamperDetected -> "tamper"
                is VaultException.UnsupportedFormatVersion -> "unsupported-fmt"
                is VaultException.NoRootKey -> "no-root"
                is VaultException.HardwareBackedKeystoreUnavailable -> "no-keystore"
                is VaultException.RecoveryFailed -> "recovery-failed"
            }
        }
        assertEquals(7, labels.size)
        assertEquals(setOf("locked", "wrong-purpose", "tamper", "unsupported-fmt", "no-root", "no-keystore", "recovery-failed"), labels.toSet())
    }
}
