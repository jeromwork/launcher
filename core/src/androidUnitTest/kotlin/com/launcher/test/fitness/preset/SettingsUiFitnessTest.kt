package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-69 T069-028 (SC-006, rule 1) + T069-030 (FR-008, §10) — the Settings
 * screen renders a data-driven [com.launcher.preset.settings.SettingsView]
 * without switching on `Component` subtypes, and `SettingsViewModel` depends
 * only on the `SettingsGateway` port, never `ReconcileEngine` directly.
 *
 * Reads across the `app/` module from a `core` test the same way
 * [PoolI18nCoverageTest] does (cwd differs between root and module runners).
 */
class SettingsUiFitnessTest {

    @Test
    fun settingsScreen_doesNotSwitchOnComponentSubtypes() {
        val file = locate("app/src/main/java/com/launcher/app/settings/SettingsScreen.kt")
            ?: return org.junit.Assert.fail("SettingsScreen.kt not found — the gate cannot verify anything.")
        val text = file.readText()
        assertTrue(
            "SC-006: SettingsScreen must not import Component (row-level data is pre-formatted by the domain builder).",
            !text.contains("import com.launcher.preset.model.Component"),
        )
        assertTrue(
            "SC-006: SettingsScreen must not `is Component.<Subtype>` switch.",
            !Regex("""is\s+Component\.\w+""").containsMatchIn(text),
        )
    }

    @Test
    fun settingsScreen_doesNotCallAndroidApisDirectly() {
        val file = locate("app/src/main/java/com/launcher/app/settings/SettingsScreen.kt")
            ?: return org.junit.Assert.fail("SettingsScreen.kt not found.")
        val forbidden = listOf("android.content.Intent", "android.app.role.RoleManager", "startActivity(")
        val text = file.readText()
        val violations = forbidden.filter { text.contains(it) }
        assertTrue(
            "FR-008: SettingsScreen must not call Android APIs directly (system-dialog rows go through onChange -> gateway -> provider).\n" +
                "Found: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun settingsViewModel_hasNoReconcileEngineReference() {
        val file = locate("app/src/main/java/com/launcher/app/settings/SettingsViewModel.kt")
            ?: return org.junit.Assert.fail("SettingsViewModel.kt not found.")
        val text = file.readText()
        assertTrue(
            "T069-030 (§10, FR-008): SettingsViewModel must depend only on SettingsGateway, never ReconcileEngine.",
            !text.contains("ReconcileEngine"),
        )
    }

    /** Test cwd is the module dir under Gradle, but not under every IDE runner — try both. */
    private fun locate(relativePath: String): File? {
        val cwd = File(System.getProperty("user.dir") ?: return null)
        return listOf(File(cwd, "../$relativePath"), File(cwd, relativePath))
            .firstOrNull { it.exists() }
    }
}
