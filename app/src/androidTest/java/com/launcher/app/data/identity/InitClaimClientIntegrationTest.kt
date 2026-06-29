package com.launcher.app.data.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for [InitClaimClient] against a live `wrangler dev`
 * identity Worker (T670, Q-M variant b).
 *
 * **Status**: `[deferred-local-emulator]`. Owner runs manually after:
 *  - `cd workers/identity && npm run dev` on the host (port 8788)
 *  - Physical device: `adb reverse tcp:8788 tcp:8788`
 *  - Emulator: BuildConfig.IDENTITY_INIT_CLAIM_WORKER_URL defaults to
 *    `http://10.0.2.2:8788` in debug.
 *
 * **Coverage when wired up**:
 *  - First call returns a fresh UUID v4 stableId.
 *  - Second call with the same uid returns the same stableId (idempotency).
 *  - claims.uid != body.uid → AuthExpired (from 403 mapping).
 */
@RunWith(AndroidJUnit4::class)
class InitClaimClientIntegrationTest {

    @Ignore("[deferred-local-emulator] — requires wrangler dev on host")
    @Test
    fun idempotencyAgainstWranglerDev_placeholder() {
        // TODO(local-emulator): inject real FirebaseTokenSupplier + assert that
        //   two calls with same uid return identical stableId without re-binding.
    }
}
