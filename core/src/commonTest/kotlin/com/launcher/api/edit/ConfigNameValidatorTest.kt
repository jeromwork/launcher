package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ConfigNameValidator]. Trace: spec 014 T035, R6 (configName
 * validation), security.md CHK009.
 */
class ConfigNameValidatorTest {

    @Test
    fun valid_ascii_name_passes() {
        val result = ConfigNameValidator.validate("home")
        assertEquals(Outcome.Success("home"), result)
    }

    @Test
    fun valid_cyrillic_name_passes() {
        val result = ConfigNameValidator.validate("Дом")
        assertEquals(Outcome.Success("Дом"), result)
    }

    @Test
    fun valid_mixed_name_with_space_and_hyphen_passes() {
        val result = ConfigNameValidator.validate("home-job 2")
        assertEquals(Outcome.Success("home-job 2"), result)
    }

    @Test
    fun trim_strips_leading_and_trailing_whitespace() {
        val result = ConfigNameValidator.validate("  home  ")
        assertEquals(Outcome.Success("home"), result)
    }

    @Test
    fun empty_string_rejected() {
        val result = ConfigNameValidator.validate("")
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_EMPTY)),
            result,
        )
    }

    @Test
    fun whitespace_only_rejected_as_empty() {
        val result = ConfigNameValidator.validate("   ")
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_EMPTY)),
            result,
        )
    }

    @Test
    fun too_long_name_rejected_at_33_chars() {
        // MAX_CONFIG_NAME_LENGTH = 32 — 33 chars must fail.
        val name = "a".repeat(33)
        val result = ConfigNameValidator.validate(name)
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_TOO_LONG)),
            result,
        )
    }

    @Test
    fun exactly_32_chars_passes() {
        val name = "a".repeat(32)
        val result = ConfigNameValidator.validate(name)
        assertEquals(Outcome.Success(name), result)
    }

    @Test
    fun emoji_rejected_as_invalid_chars() {
        // Emoji blocked per research.md §5 — Firestore-path-encoding защита для F-014.1.
        val result = ConfigNameValidator.validate("home 🏠")
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_INVALID_CHARS)),
            result,
        )
    }

    @Test
    fun underscore_rejected_as_invalid_chars() {
        // Underscore NOT in `\p{L}\p{N}` + space + hyphen → InvalidChars.
        val result = ConfigNameValidator.validate("my_config")
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_INVALID_CHARS)),
            result,
        )
    }

    @Test
    fun slash_rejected_as_invalid_chars() {
        // Slash would break Firestore paths в F-014.1 → reject early.
        val result = ConfigNameValidator.validate("home/job")
        assertEquals(
            Outcome.Failure(StoreError.InvalidName(ConfigNameValidator.REASON_INVALID_CHARS)),
            result,
        )
    }

    @Test
    fun dot_rejected_as_invalid_chars() {
        val result = ConfigNameValidator.validate("home.cfg")
        assertTrue(
            result is Outcome.Failure &&
                (result.error as StoreError.InvalidName).reason == ConfigNameValidator.REASON_INVALID_CHARS,
            "dots not in allowed set",
        )
    }

    @Test
    fun digits_only_passes() {
        val result = ConfigNameValidator.validate("2026")
        assertEquals(Outcome.Success("2026"), result)
    }
}
