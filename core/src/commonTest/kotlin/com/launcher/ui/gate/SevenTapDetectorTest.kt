package com.launcher.ui.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 010 T094 — verifies the 7-tap detector обеспечивает FR-021
 * constraints (a)/(b)/(c) AND fires the gate callback exactly once on the
 * 7-th valid tap.
 */
class SevenTapDetectorTest {

    @Test
    fun seven_taps_within_window_and_delta_trigger_admin_gate() {
        var triggered = 0
        val detector = SevenTapDetector(onAdminGateTriggered = { triggered += 1 })
        val stages = (0 until 7).map { detector.onTap(100f, 100f, it * 500L) }

        assertEquals(1, triggered)
        // Stages 1..3 light, 4..6 medium, 7 success.
        assertEquals(
            listOf(
                SevenTapDetector.Stage.Light, SevenTapDetector.Stage.Light, SevenTapDetector.Stage.Light,
                SevenTapDetector.Stage.Medium, SevenTapDetector.Stage.Medium, SevenTapDetector.Stage.Medium,
                SevenTapDetector.Stage.Success,
            ),
            stages,
        )
    }

    @Test
    fun tap_outside_48dp_delta_resets_counter() {
        var triggered = 0
        val detector = SevenTapDetector(onAdminGateTriggered = { triggered += 1 })
        // 3 taps near (100, 100).
        repeat(3) { detector.onTap(100f, 100f, it * 100L) }
        // 4th tap > 48 dp away — counter resets, this is the new tap 1.
        val resetStage = detector.onTap(200f, 100f, 400L)
        assertEquals(SevenTapDetector.Stage.Light, resetStage)
        // 6 more near (200, 100) → tap 7 reached.
        (0 until 6).forEach { detector.onTap(200f, 100f, 500L + it * 100L) }
        assertEquals(1, triggered)
    }

    @Test
    fun tap_after_5sec_window_resets_counter() {
        var triggered = 0
        val detector = SevenTapDetector(onAdminGateTriggered = { triggered += 1 })
        repeat(6) { detector.onTap(100f, 100f, it * 100L) }
        // 7-th tap is at 5_001 ms (>5000 ms window) — resets.
        val late = detector.onTap(100f, 100f, 5_001L)
        assertEquals(SevenTapDetector.Stage.Light, late)
        assertEquals(0, triggered)
    }

    @Test
    fun reset_clears_state_so_next_seven_taps_can_trigger_again() {
        var triggered = 0
        val detector = SevenTapDetector(onAdminGateTriggered = { triggered += 1 })
        repeat(7) { detector.onTap(50f, 50f, it * 100L) }
        assertEquals(1, triggered)
        // Detector auto-resets on Success — verify by triggering a second
        // chain immediately.
        repeat(7) { detector.onTap(50f, 50f, 1000L + it * 100L) }
        assertEquals(2, triggered)
    }

    @Test
    fun small_jitter_within_48dp_stays_in_chain() {
        var triggered = 0
        val detector = SevenTapDetector(onAdminGateTriggered = { triggered += 1 })
        // Jitter within ±48 dp — chain holds.
        val offsets = listOf(0f, 5f, -10f, 30f, -40f, 47f, 0f)
        offsets.forEachIndexed { i, dx ->
            detector.onTap(100f + dx, 100f, i * 100L)
        }
        assertEquals(1, triggered)
    }

    @Test
    fun nine_dp_outside_delta_does_not_trigger_chain() {
        var triggered = 0
        val detector = SevenTapDetector(
            deltaDp = 10f,
            onAdminGateTriggered = { triggered += 1 },
        )
        // First tap at (100, 100).
        detector.onTap(100f, 100f, 0L)
        // Second tap at (115, 100) — Δx=15 > 10 → resets to count=1.
        detector.onTap(115f, 100f, 100L)
        // Now 5 more near (115, 100) → reaches 6 total in this chain.
        repeat(5) { detector.onTap(115f, 100f, 200L + it * 50L) }
        assertEquals(0, triggered, "only 6 taps in current chain — gate not fired")
        // 7th caps it.
        detector.onTap(115f, 100f, 500L)
        assertEquals(1, triggered)
    }

    @Test
    fun custom_requiredTaps_lower_count_triggers_faster() {
        var triggered = 0
        val detector = SevenTapDetector(
            requiredTaps = 3,
            onAdminGateTriggered = { triggered += 1 },
        )
        repeat(3) { detector.onTap(50f, 50f, it * 100L) }
        assertTrue(triggered == 1, "lowered requiredTaps short-circuits chain")
    }
}
