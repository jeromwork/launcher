package com.launcher.ui.components.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit test для [PushIndicatorPresenter] — pure state-machine logic.
 *
 * Per spec 008 Phase 8 T102 + SC-001 (every push has visible UI state).
 *
 * Compose UI tests (Robolectric / Android instrumentation) will exercise the
 * composable rendering separately — these tests cover the **state transitions**
 * that drive the UI.
 */
class PushIndicatorPresenterTest {

    @Test
    fun initial_state_is_Idle() {
        // Идея: presenter doesn't manage state itself; consumer initializes
        // to Idle. Verify reset() returns Idle.
        assertEquals(PushIndicatorState.Idle, PushIndicatorPresenter.reset())
    }

    @Test
    fun push_initiated_transitions_to_InProgress() {
        val state = PushIndicatorPresenter.onPushInitiated()
        assertEquals(PushIndicatorState.InProgress, state)
    }

    @Test
    fun slow_push_with_no_network_transitions_to_InProgressNoNetwork() {
        val state = PushIndicatorPresenter.onPushSlowWithoutNetwork(PushIndicatorState.InProgress)
        assertEquals(PushIndicatorState.InProgressNoNetwork, state)
    }

    @Test
    fun slow_push_does_not_affect_other_states() {
        // SlowWithoutNetwork только перетекает из InProgress. Других states
        // не трогает (e.g., после Sent — больше не показываем «нет интернета»).
        val states = listOf(
            PushIndicatorState.Idle,
            PushIndicatorState.Sent,
            PushIndicatorState.AppliedOnDevice,
        )
        for (s in states) {
            val result = PushIndicatorPresenter.onPushSlowWithoutNetwork(s)
            assertEquals(s, result, "Slow trigger should not affect state: $s")
        }
    }

    @Test
    fun firestore_ack_transitions_to_Sent() {
        assertEquals(PushIndicatorState.Sent, PushIndicatorPresenter.onFirestoreAcked())
    }

    @Test
    fun state_applied_transitions_to_AppliedOnDevice() {
        assertEquals(PushIndicatorState.AppliedOnDevice, PushIndicatorPresenter.onStateAppliedOnDevice())
    }

    @Test
    fun push_failed_carries_reason() {
        val state = PushIndicatorPresenter.onPushFailed("Network timeout")
        assertTrue(state is PushIndicatorState.Failed)
        assertEquals("Network timeout", (state as PushIndicatorState.Failed).reason)
    }

    @Test
    fun reset_returns_to_Idle() {
        assertEquals(PushIndicatorState.Idle, PushIndicatorPresenter.reset())
    }

    @Test
    fun no_network_threshold_matches_spec_FR_015_5s() {
        // Verify the threshold константа matches FR-015 / SC-001 5-second budget.
        assertEquals(5_000L, PushIndicatorPresenter.NO_NETWORK_WARNING_AFTER_MS)
    }
}
