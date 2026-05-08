package com.launcher.test.fitness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec 005 §8.4 / Clarification C5 / T622: the `migrateLegacyAction`
 * forward-compat bridge in [com.launcher.api.action.ActionWireFormat] is
 * scheduled to expire when spec 006 lands. Until then it must stay; once
 * spec 006 is merged the function plus all `legacy-spec003-*.json` fixtures
 * must be removed.
 *
 * The deadline is read from a build-time constant (declared in
 * `core/build.gradle.kts` as `migrateLegacyActionDeadlineSpec = "006"`).
 * This Kotlin test mirrors the constant locally — they must match.
 *
 * **How the gate switches on**: when the latest merged spec id (read from
 * the `specs/` directory layout) is >= `MIGRATE_LEGACY_ACTION_DEADLINE_SPEC`,
 * the test asserts that `migrateLegacyAction` no longer exists in the
 * source tree.
 */
class LegacyMigrationExpiryTest {

    private val deadlineSpec = "006"
    private val bridgeAnchor = "LEGACY-BRIDGE-EXPIRES-IN-SPEC-006"
    private val bridgeFile: File by lazy {
        val rel = "src/commonMain/kotlin/com/launcher/api/action/ActionWireFormat.kt"
        File(rel).takeIf { it.exists() } ?: File("core/$rel")
    }

    @Test
    fun bridge_isStillPresent_untilDeadlineSpecLands() {
        val latestSpec = latestMergedSpecId()
        if (latestSpec < deadlineSpec) {
            assertTrue(
                "ActionWireFormat.kt missing — bridge anchor $bridgeAnchor expected before spec $deadlineSpec lands",
                bridgeFile.exists(),
            )
            val text = bridgeFile.readText()
            assertTrue(
                "ActionWireFormat.kt must keep migrateLegacyAction until spec $deadlineSpec lands; latestSpec=$latestSpec",
                text.contains("migrateLegacyAction"),
            )
            assertTrue(
                "ActionWireFormat.kt must keep grep anchor $bridgeAnchor until spec $deadlineSpec lands",
                text.contains(bridgeAnchor),
            )
        }
    }

    @Test
    fun bridge_isRemoved_onceDeadlineSpecLands() {
        val latestSpec = latestMergedSpecId()
        if (latestSpec >= deadlineSpec) {
            // After spec 006 lands, the bridge plus fixtures must be deleted.
            // The mirroring `whatsappResidueTest` covers fixture file deletion.
            val text = if (bridgeFile.exists()) bridgeFile.readText() else ""
            assertFalse(
                "Spec $deadlineSpec has landed (latestSpec=$latestSpec) — migrateLegacyAction must be removed.",
                text.contains("migrateLegacyAction"),
            )
        }
    }

    /**
     * Reads `specs/<id>-<slug>/` directory names and returns the highest
     * three-digit prefix (e.g. "005"). Defaults to "000" if the directory
     * is missing — keeps the test runnable on a partial checkout.
     */
    private fun latestMergedSpecId(): String {
        val specsDir = listOf("../specs", "specs").map { File(it) }.firstOrNull { it.isDirectory }
            ?: return "000"
        return specsDir.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir -> Regex("^(\\d{3})-").find(dir.name)?.groupValues?.get(1) }
            ?.maxOrNull()
            ?: "000"
    }
}
