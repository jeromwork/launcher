package com.launcher.api.pairing

import kotlin.jvm.JvmInline
import kotlin.random.Random

/**
 * Single-use, 5-minute-TTL pairing token (spec 007 §FR-003, OWD-3).
 *
 *  - **Alphabet** ([ALPHABET]): 32 chars excluding visually-similar `0`, `O`,
 *    `I`, `1` so the user can read aloud if needed (senior-safe).
 *  - **Length**: 6 chars → 32^6 ≈ 1.07B combinations; collisions vanish under
 *    the 5-minute TTL window (R10 in plan.md §Risks).
 *  - **Format**: enforced by [REGEX]; the constructor throws on invalid input
 *    so adapters can rely on the type system (no defensive re-validation).
 *
 * Domain-neutral by design — same primitive is reused for future trust-pairing
 * (contacts spec 011, jitsi calls, multi-admin) per `TrustEdgeBootstrap` and
 * memory `project_qr_pairing_trust_primitive.md`.
 */
@JvmInline
value class PairingToken(val raw: String) {
    init {
        require(REGEX.matches(raw)) { "Invalid pairing token format: '$raw' (expected ${REGEX.pattern})" }
    }

    companion object {
        const val LENGTH: Int = 6

        /** Alphabet excluding 0/O/I/1 — 32 characters, base-32-friendly. */
        const val ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        val REGEX: Regex = Regex("^[A-HJ-NP-Z2-9]{$LENGTH}$")

        fun generate(random: Random = Random.Default): PairingToken =
            PairingToken(buildString(LENGTH) {
                repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            })
    }
}
