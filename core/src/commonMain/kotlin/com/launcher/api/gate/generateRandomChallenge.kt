package com.launcher.api.gate

import kotlin.random.Random

/**
 * Free function generating a fresh [Challenge] for the 7-tap admin gate
 * (spec 010 FR-023, plan §11 C-4 — free function, NOT a class / interface).
 *
 * Uniform choice between [Challenge.NumericEntry] and [Challenge.SequenceTap]:
 *  - 50 % NumericEntry, random 1000..9999.
 *  - 50 % SequenceTap, 6 buttons numbered 1..6 в random visual order, 3 of
 *    them selected as the answer in random order.
 *
 * The seedable [random] parameter exists for the statistical FP test
 * (`ChallengeFPRateTest`, T025) and for deterministic Compose-state restoration
 * tests; production callers pass [Random.Default].
 */
fun generateRandomChallenge(random: Random = Random.Default): Challenge =
    if (random.nextBoolean()) {
        Challenge.NumericEntry(answer = (1000 + random.nextInt(9000)).toString())
    } else {
        val buttonIds = (1..6).shuffled(random)
        val expectedOrder = buttonIds.shuffled(random).take(3)
        Challenge.SequenceTap(buttonIds = buttonIds, expectedOrder = expectedOrder)
    }
