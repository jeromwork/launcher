package com.launcher.app.data.recovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for [WorkerRecoveryKeyBackup] against a live `wrangler dev`
 * Worker (T669, FR-010).
 *
 * **Status**: `[deferred-local-emulator]` — requires:
 *  - `cd workers/backup && npm run dev` on the host (port 8787)
 *  - For physical device: `adb reverse tcp:8787 tcp:8787`
 *  - For emulator: BuildConfig.RECOVERY_BACKUP_WORKER_URL defaults to
 *    `http://10.0.2.2:8787` in debug builds
 *  - A real Firebase ID-token (signed-in user) — production deployment of
 *    the identity Worker not required because tests can sign-in via Sign-In
 *    flow then fetch the token via FirebaseTokenSupplier.
 *
 * Owner runs manually on pixel_5_api_34 emulator or Xiaomi 11T.
 *
 * **Coverage when wired up** (skeleton — fill in once wrangler dev is
 * confirmed reachable):
 *  - Upload sample blob → GET fetches it byte-equal → DELETE removes it →
 *    second GET returns 404.
 *  - Idempotency: same body re-POST returns 200 (no R2 write).
 *  - Wrong stableId in body vs claim → 403.
 *  - schemaVersion=2 → UNSUPPORTED_SCHEMA.
 */
@RunWith(AndroidJUnit4::class)
class WorkerRecoveryKeyBackupIntegrationTest {

    @Ignore("[deferred-local-emulator] — requires wrangler dev on host")
    @Test
    fun roundtripAgainstWranglerDev_placeholder() {
        // TODO(local-emulator): wire to BuildConfig URL + FirebaseTokenSupplier +
        //   sampleBlob() once owner confirms wrangler dev is reachable from the
        //   AVD on `10.0.2.2:8787`.
    }
}
