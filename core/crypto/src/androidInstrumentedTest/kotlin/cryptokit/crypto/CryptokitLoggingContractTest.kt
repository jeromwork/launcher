package cryptokit.crypto

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TASK-51 Phase 9 T103 — FR-017 logging contract smoke.
 *
 * Triggers a logcat write under tag `cryptokit` using the exact format the
 * production coordinator uses (operation/exceptionClass/messageHash). Owner
 * verifies via `adb logcat -s cryptokit` that the line appears and contains
 * no raw bytes, hex>8B, or deviceIds. This test passes unconditionally — it
 * exists to produce an observable logcat event, not to assert it (capturing
 * system log inside an instrumented test is hostile to flake).
 *
 * The static side (no Log.w with raw bytes/hex/deviceIds in production code)
 * is enforced by `NoBackdoorLoggingTest` (Konsist, Phase 8 T073).
 */
@RunWith(AndroidJUnit4::class)
class CryptokitLoggingContractTest {

    @Test
    fun emitsContractCompliantLogLine() {
        Log.w(
            "cryptokit",
            "operation=__smoke-test exceptionClass=KeyStoreException " +
                "messageHash=${"simulated tee unavailable".hashCode()}",
        )
    }
}
