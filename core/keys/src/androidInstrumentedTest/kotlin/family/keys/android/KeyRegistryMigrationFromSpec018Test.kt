package family.keys.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Byte-equal migration test from spec 018 ConfigCipher2 ciphertext to the
 * task-6 KeyRegistry-aware data path (T672, FR-018, SC-004).
 *
 * **Status**: `[deferred-local-emulator]` — and additionally **gated on T630**
 * (capturing the spec-018 sample ciphertext fixture). Owner runs after:
 *  1. T630 — run spec-018 ConfigCipher2 once on the emulator to produce
 *     `core/keys/src/androidInstrumentedTest/resources/config-ciphertext-spec018-sample.bin`.
 *  2. This test (T672) — read fixture → instantiate AndroidKeystoreRegistry +
 *     KeyRegistry.derive(stableId, "config") → decrypt → assert plaintext
 *     byte-equal to the spec-018 plaintext fixture.
 *
 * **NOTE on T671**: the original task wording said to "refactor ConfigCipher2
 * to use KeyRegistry.derive(stableId, 'config') for key material". On
 * inventory we found ConfigCipher2 is the **envelope-pattern** cipher
 * introduced by spec 011 — it generates a fresh random CEK per `seal()` and
 * seals it under per-recipient X25519 keys (see EnvelopeConfigCipherImpl).
 * There is no root-key-derived key material in that path, so the textual
 * refactor T671 describes is not applicable. The migration concern T671
 * is really pointing at is **fixture round-trip** — that old ciphertext
 * produced before the AndroidKeystoreRegistry landed must still decrypt.
 * That is exactly what this test (T672) covers when wired up.
 *
 * If a later spec re-introduces a root-key-derived per-namespace cipher,
 * THAT path would consume KeyRegistry.derive(...). Not now.
 */
@RunWith(AndroidJUnit4::class)
class KeyRegistryMigrationFromSpec018Test {

    @Ignore("[deferred-local-emulator] — requires T630 fixture capture first")
    @Test
    fun spec018CiphertextRoundtripsThroughKeyRegistry_placeholder() {
        // TODO(local-emulator + T630): load
        //   core/keys/src/androidInstrumentedTest/resources/config-ciphertext-spec018-sample.bin
        //   then decrypt via the current EnvelopeConfigCipherImpl + DeviceIdentity
        //   path and assert plaintext byte-equal to the recorded fixture.
    }
}
