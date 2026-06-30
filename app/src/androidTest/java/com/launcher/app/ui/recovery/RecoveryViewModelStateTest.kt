package com.launcher.app.ui.recovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Process-kill resilience test for [RecoveryViewModel] (T652, FR-017).
 *
 * **Status**: `[deferred-local-emulator]` — requires
 * `androidx.lifecycle:lifecycle-viewmodel-savedstate` runtime + an AVD to
 * simulate Activity recreation.
 *
 * **Why also marked as a TODO in the production code**: the current
 * [RecoveryViewModel] does NOT yet use `SavedStateHandle` to persist its
 * UI state across process death — the `_state` MutableStateFlow lives only
 * in memory. Adding `SavedStateHandle` (and the serialization of `State`)
 * is a follow-up that must land before this test stops being trivially
 * green / red.
 *
 * **Coverage when wired up** (sketch — fill in when AVD + SavedStateHandle
 * integration land):
 *  - VM enters Restoring → process recreate → VM restored at Restoring.
 *  - VM enters Fallback(NO_VAULT) → recreate → VM restored at Fallback.
 *  - Pending CharArray for passphrase prompt is **NOT** persisted
 *    (security — never round-trip cleartext through SavedStateHandle).
 */
@RunWith(AndroidJUnit4::class)
class RecoveryViewModelStateTest {

    @Test
    fun placeholder_processKillRestoresLastNonPassphraseState() {
        // TODO(viewmodel-savedstate-wiring): once RecoveryViewModel switches to
        //   SavedStateHandle, simulate Activity recreate via composeRule or
        //   ActivityScenario.recreate() and assert state shape survives.
    }
}
