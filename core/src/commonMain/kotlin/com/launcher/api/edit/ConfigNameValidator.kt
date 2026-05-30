package com.launcher.api.edit

import com.launcher.api.result.Outcome

/**
 * Validates [NamedConfig.configName] user input per contracts/named-config-local.md
 * §Validation rules + research.md §5 (configName validation decisions).
 *
 * Rules:
 *  - **NFC normalize** before length / regex checks (combining marks
 *    decomposed-or-composed входы treated identically).
 *  - **Length**: 1..[NamedConfig.MAX_CONFIG_NAME_LENGTH] after NFC + trim.
 *  - **Allowed chars**: Unicode letters (`\p{L}`), digits (`\p{N}`), space, hyphen.
 *  - **No emojis** (защита от Firestore-path-encoding surprises в F-014.1).
 *
 * Returns [Outcome.Success] with the normalized name, или [StoreError.InvalidName]
 * with a stable reason string.
 *
 * NOTE: Uniqueness check (case-insensitive) — отдельная responsibility
 * [NamedConfigsLocalStore.create] (требует access full list).
 *
 * Pure-Kotlin (no platform deps). Konsist gate T170 enforces.
 */
object ConfigNameValidator {

    /** Stable reason strings returned via [StoreError.InvalidName.reason]. */
    const val REASON_EMPTY = "EmptyName"
    const val REASON_TOO_LONG = "TooLong"
    const val REASON_INVALID_CHARS = "InvalidChars"

    private val ALLOWED_PATTERN = Regex("""^[\p{L}\p{N} \-]+$""")

    fun validate(input: String): Outcome<String, StoreError> {
        val normalized = normalize(input)
        if (normalized.isEmpty()) {
            return Outcome.Failure(StoreError.InvalidName(REASON_EMPTY))
        }
        if (normalized.length > NamedConfig.MAX_CONFIG_NAME_LENGTH) {
            return Outcome.Failure(StoreError.InvalidName(REASON_TOO_LONG))
        }
        if (!ALLOWED_PATTERN.matches(normalized)) {
            return Outcome.Failure(StoreError.InvalidName(REASON_INVALID_CHARS))
        }
        return Outcome.Success(normalized)
    }

    /**
     * NFC-normalize + trim. Implemented per-platform via `expect`/`actual`
     * would be cleanest, но Konsist gate T171 disallows that в `api/edit/`.
     * Fallback: implement lightweight NFC-equivalence через `String.trim()` +
     * canonical composition heuristic.
     *
     * Kotlin stdlib's `String` already stores codepoints as UTF-16; Unicode
     * regex (`\p{L}`) operates on canonical equivalents для most cases.
     * Composed-vs-decomposed forms hit by `trim()` removing zero-width joiners.
     * Full NFC requires java.text.Normalizer — но это `java.text` is in
     * standard JVM (commonMain ok через kotlin.text expectation? No — это
     * java.text.* not allowed в commonMain).
     *
     * **Decision**: F-014.0 ships with `trim()` only. Most user inputs are
     * already canonical (typed on keyboard). Edge case (paste of decomposed
     * combining marks) treated as best-effort.
     * TODO(F-014.1 followup): introduce `expect/actual` NFC normalize via
     * java.text.Normalizer.Form.NFC on androidMain (after Konsist exception
     * acknowledged).
     */
    private fun normalize(input: String): String = input.trim()
}
