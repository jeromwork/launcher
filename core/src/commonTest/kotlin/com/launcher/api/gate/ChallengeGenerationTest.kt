package com.launcher.api.gate

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 010 T024 — verifies [generateRandomChallenge] uniform distribution
 * between [Challenge.NumericEntry] and [Challenge.SequenceTap] over 1000
 * iterations, plus value-range invariants per FR-023.
 *
 * χ² acceptance: with 1000 trials and an expected 50/50 split, the count
 * must fall between 400 and 600 (very loose ≈ ±3σ for σ ≈ √250 ≈ 15.8,
 * so ±50 covers >99.99% of fair distributions).
 */
class ChallengeGenerationTest {

    @Test
    fun uniform_distribution_between_variants_over_1000_iterations() {
        val random = Random(seed = 42)
        var numeric = 0
        var sequence = 0
        repeat(1000) {
            when (generateRandomChallenge(random)) {
                is Challenge.NumericEntry -> numeric++
                is Challenge.SequenceTap -> sequence++
            }
        }
        assertTrue(numeric in 400..600, "numeric count out of range: $numeric")
        assertTrue(sequence in 400..600, "sequence count out of range: $sequence")
        assertEquals(1000, numeric + sequence)
    }

    @Test
    fun numeric_entry_answer_in_range_1000_to_9999() {
        val random = Random(seed = 7)
        repeat(500) {
            val challenge = generateRandomChallenge(random)
            if (challenge is Challenge.NumericEntry) {
                val n = challenge.answer.toInt()
                assertTrue(n in 1000..9999, "answer out of range: ${challenge.answer}")
                assertEquals(4, challenge.answer.length)
            }
        }
    }

    @Test
    fun sequence_tap_has_six_buttons_and_three_position_order() {
        val random = Random(seed = 13)
        repeat(500) {
            val challenge = generateRandomChallenge(random)
            if (challenge is Challenge.SequenceTap) {
                assertEquals(6, challenge.buttonIds.size, "buttonIds size")
                assertEquals(6, challenge.buttonIds.toSet().size, "buttonIds must be distinct")
                assertEquals(3, challenge.expectedOrder.size, "expectedOrder size")
                assertEquals(3, challenge.expectedOrder.toSet().size, "expectedOrder must be distinct")
                assertTrue(
                    challenge.expectedOrder.all { it in challenge.buttonIds },
                    "expectedOrder must be a subset of buttonIds",
                )
                assertTrue(
                    challenge.buttonIds.toSet() == (1..6).toSet(),
                    "buttonIds must be a permutation of 1..6",
                )
            }
        }
    }
}
