package com.launcher.api.wizard.data

import com.launcher.api.wizard.AttestationRecord
import com.launcher.api.wizard.DismissedHintsState
import com.launcher.api.wizard.ThemeChoice
import com.launcher.api.wizard.UserPreferences
import com.launcher.api.wizard.WizardCheckpoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistentFormatRoundtripTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun wizardCheckpoint_roundtrip() {
        val original = WizardCheckpoint(
            schemaVersion = 1,
            manifestId = "wizard-manifest.simple-launcher",
            currentStepIndex = 3,
            answers = mapOf(
                "language" to JsonPrimitive("ru"),
                "tileSet" to JsonPrimitive("classic-6"),
            ),
        )
        val encoded = json.encodeToString(WizardCheckpoint.serializer(), original)
        val decoded = json.decodeFromString(WizardCheckpoint.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun userPreferences_roundtrip() {
        val original = UserPreferences(
            schemaVersion = 1,
            theme = ThemeChoice.Dark,
            fontScale = 1.3f,
            languageOverride = "ru",
            attestedSettings = mapOf(
                "android.hide_status_bar" to AttestationRecord(
                    attestedAtEpochMillis = 1_700_000_000_000L,
                    value = true,
                ),
            ),
            wizardCompletedPresets = setOf("simple-launcher"),
        )
        val encoded = json.encodeToString(UserPreferences.serializer(), original)
        val decoded = json.decodeFromString(UserPreferences.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun dismissedHints_roundtrip() {
        val original = DismissedHintsState(ids = setOf("first-tile-hint", "back-gesture-hint"))
        val encoded = json.encodeToString(DismissedHintsState.serializer(), original)
        val decoded = json.decodeFromString(DismissedHintsState.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
