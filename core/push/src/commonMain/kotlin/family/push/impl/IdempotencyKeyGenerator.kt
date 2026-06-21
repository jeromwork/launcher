package family.push.impl

import kotlin.random.Random

/**
 * T050 — Generates UUID v4 strings для `Idempotency-Key` header. Per FR-010,
 * FR-025.
 *
 * Injection-friendly interface (caller can substitute [FixedIdempotencyKeyGenerator]
 * в tests для deterministic assertions per CHK010 dev-experience).
 */
interface IdempotencyKeyGenerator {
    fun next(): String
}

/**
 * Default impl — uses Kotlin stdlib Random для генерации UUID v4. Multiplatform
 * (no kotlin.util.UUID — added только в Kotlin 2.0.20 stdlib). Manual RFC 4122
 * compliant UUID v4 generator.
 *
 * SECURITY NOTE: idempotency key — NOT secret. It just needs to be unique per
 * trigger (collision probability с UUID v4: 1 в 5×10^36 для 100K events/day —
 * не существенно).
 */
class RandomUuidV4IdempotencyKeyGenerator : IdempotencyKeyGenerator {
    override fun next(): String {
        val bytes = ByteArray(16).apply { Random.nextBytes(this) }
        // RFC 4122 §4.4 — version 4 + variant 1.
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()

        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}

/**
 * Test-only deterministic generator. Cycles через [values] list, wrapping
 * around на end. Use в unit tests assertion-friendly idempotency keys.
 */
class FixedIdempotencyKeyGenerator(private val values: List<String>) : IdempotencyKeyGenerator {
    constructor(vararg values: String) : this(values.toList())

    private var index = 0
    override fun next(): String {
        val value = values[index % values.size]
        index++
        return value
    }
}
