package cryptokit.crypto.property

import cryptokit.crypto.api.values.KeyId
import cryptokit.crypto.api.values.KeyNamespace
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeyIdPrefixPropertyTest {

    private val alphabet = ('a'..'z') + ('0'..'9')

    @Test
    fun validPrefixWithKebabSuffix_accepted_100Iterations() {
        val rng = Random(seed = 2024)
        repeat(100) {
            val prefix = KeyNamespace.allPrefixes().random(rng)
            val suffix = (1..(rng.nextInt(2, 5))).joinToString("-") {
                (1..rng.nextInt(2, 8)).map { alphabet.random(rng) }.joinToString("")
            }
            val raw = prefix + suffix
            val id = KeyId(raw)
            assertTrue(id.raw.startsWith(prefix), "expected prefix $prefix on $raw")
        }
    }

    @Test
    fun unknownPrefix_rejected_100Iterations() {
        val rng = Random(seed = 17)
        repeat(100) {
            val unknownPrefix = (1..rng.nextInt(3, 8)).map { alphabet.random(rng) }.joinToString("") + "-"
            // Skip accidental collisions with valid prefixes (very unlikely but be safe).
            if (KeyNamespace.allPrefixes().any { unknownPrefix.startsWith(it) || it.startsWith(unknownPrefix) }) return@repeat
            val raw = unknownPrefix + "foo-bar"
            assertFailsWith<IllegalArgumentException> { KeyId(raw) }
        }
    }
}
