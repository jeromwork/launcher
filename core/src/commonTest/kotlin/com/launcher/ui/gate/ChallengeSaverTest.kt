package com.launcher.ui.gate

import com.launcher.api.gate.Challenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Spec 010 C-1 — verifies [ChallengeSaver.restore] correctly decodes the
 * encodings emitted by save, AND fails gracefully on corrupted Bundle data.
 *
 * The save direction is exercised only indirectly here (the encoding format
 * is locked by these test cases — adding a new variant requires touching
 * this file). Compose's own `rememberSaveable` machinery covers the
 * SaverScope.save dispatch path в `ChallengeGateScreenTest`.
 */
class ChallengeSaverTest {

    @Test
    fun restore_decodes_numericEntry_encoding() {
        val restored = ChallengeSaver.restore(listOf("N", "8472"))
        assertEquals(Challenge.NumericEntry(answer = "8472"), restored)
    }

    @Test
    fun restore_decodes_sequenceTap_encoding() {
        val restored = ChallengeSaver.restore(listOf("S", "3,5,1,6,4,2", "5,4,1"))
        assertEquals(
            Challenge.SequenceTap(
                buttonIds = listOf(3, 5, 1, 6, 4, 2),
                expectedOrder = listOf(5, 4, 1),
            ),
            restored,
        )
    }

    @Test
    fun restore_returns_null_for_corrupted_encoding() {
        assertNull(ChallengeSaver.restore(listOf("Q", "garbage")))
        assertNull(ChallengeSaver.restore(emptyList()))
        // Malformed Numeric (missing answer) — runCatching swallows IOOBE.
        assertNull(ChallengeSaver.restore(listOf("N")))
        // SequenceTap with non-int button id — toInt throws → null.
        assertNull(ChallengeSaver.restore(listOf("S", "1,X,3", "1,2,3")))
    }
}
