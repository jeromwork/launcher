package com.launcher.api.pairing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Format tests for [PairingToken]. Verifies the alphabet (excludes
 * visually-similar 0/O/I/1) and the regex-enforced length.
 */
class PairingTokenTest {

    @Test
    fun generate_validates_alphabet() {
        val seed = 0L
        val token = PairingToken.generate(Random(seed))
        assertEquals(PairingToken.LENGTH, token.raw.length)
        for (ch in token.raw) {
            assertTrue(ch in PairingToken.ALPHABET, "Char '$ch' not in alphabet")
        }
    }

    @Test
    fun generate_produces_only_alphabet_chars_over_many_iterations() {
        val random = Random(42)
        repeat(1_000) {
            val token = PairingToken.generate(random)
            for (ch in token.raw) {
                assertTrue(ch in PairingToken.ALPHABET)
            }
        }
    }

    @Test
    fun regex_rejects_visually_similar_chars() {
        // 0, O, I, 1 are forbidden — senior-safe alphabet.
        listOf("0BCDEF", "ABCDEO", "AB1DEF", "ABCDEI").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("Expected reject for $bad") {
                PairingToken(bad)
            }
        }
    }

    @Test
    fun regex_rejects_wrong_length() {
        listOf("", "A", "ABCDE", "ABCDEF7").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("Expected reject for $bad") {
                PairingToken(bad)
            }
        }
    }

    @Test
    fun regex_accepts_valid_token() {
        // Sanity — should not throw.
        listOf("A3KX9B", "ZN7P2W", "ABCDEF", "234567").forEach { good ->
            val token = PairingToken(good)
            assertEquals(good, token.raw)
        }
    }
}
