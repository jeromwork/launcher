package com.launcher.api.gate

/**
 * 7-tap admin gate challenge variant (spec 010 FR-023).
 *
 * Two variants chosen with uniform probability by [generateRandomChallenge] to
 * keep the false-positive rate bounded (SC-007 ≤ 1% from random taps):
 *
 *  - [NumericEntry]: user types the 4-digit number displayed in small (≤ 14 sp,
 *    FR-026) font on a custom 56 dp keypad. Theoretical FP ≈ 0.01 % per trial.
 *  - [SequenceTap]: user taps 3 of 6 numbered buttons in the order shown on
 *    screen. Theoretical FP ≈ 1/120 ≈ 0.83 % per trial.
 *
 * Pure data — kept in commonMain so the gesture/state logic is testable
 * without Android (enforced by [com.launcher.test.fitness.Spec010IsolationTest.T008]).
 */
sealed class Challenge {

    /**
     * Random 4-digit number (1000..9999) the user must re-enter on the keypad.
     * Range chosen so the display is visually readable while keeping the
     * FP rate below SC-007.
     */
    data class NumericEntry(val answer: String) : Challenge()

    /**
     * 6 numbered buttons in a random visual layout; the user must tap 3 of
     * them in the order given by [expectedOrder] (a permutation of three
     * distinct indexes into [buttonIds]).
     *
     * Invariant: [expectedOrder].size == 3 && [buttonIds].size == 6 &&
     * [expectedOrder].toSet() ⊆ [buttonIds].toSet().
     */
    data class SequenceTap(
        val buttonIds: List<Int>,
        val expectedOrder: List<Int>,
    ) : Challenge()
}
