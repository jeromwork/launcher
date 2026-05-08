package com.launcher.test.fitness

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Spec 005 §8.3 / T611 fitness gate: spec 002 type names must NOT come
 * back into the source tree once Phase 6 has deleted them. If someone
 * accidentally re-introduces (or copy-pastes) a name from the forbidden
 * list, this test fails on `./gradlew :core:test`.
 *
 * Search scope is the live source tree only (commonMain, androidMain,
 * iosMain, commonTest, androidUnitTest). Spec docs and historical
 * governance documents are deliberately *not* scanned — the residue gate
 * is about live code, not documentation.
 */
class WhatsAppResidueTest {

    private val forbidden = listOf(
        "WhatsAppHandoff",
        "ReturnContextStore",
        "ActionCycleGuard",
        "CommunicationConfigValidator",
        "WhatsAppLaunchabilityResolver",
        "RestoreOutcomeEvaluator",
        "CommunicationDiagnostics",
        "CommunicationActionType",
        "CommunicationDiagnosticEventType",
        "CommunicationWarningCode",
    )

    private val sourceRoots: List<File> by lazy {
        // Test CWD differs between IDE (:core) and Gradle (repo root). Pick prefix.
        val prefix = if (File("src/commonMain").isDirectory) "" else "core/"
        listOf("commonMain", "androidMain", "iosMain", "commonTest", "androidUnitTest")
            .map { File("${prefix}src/$it").absoluteFile }
    }

    @Test
    fun forbiddenSymbols_doNotAppearInLiveSourceTree() {
        val hits = mutableListOf<String>()
        for (root in sourceRoots) {
            if (!root.isDirectory) continue
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                .filterNot { it.parentFile?.name == "fitness" } // skip self (this test file lives in com/launcher/test/fitness/)
                .forEach { file ->
                    val text = file.readText()
                    for (sym in forbidden) {
                        if (text.contains(sym)) hits += "${file.path}: contains '$sym'"
                    }
                }
        }
        assertEquals(
            "Spec 002/003 residue must stay deleted (spec 005 §8.3 / T611).\n" +
                "Hits:\n${hits.joinToString("\n")}",
            emptyList<String>(), hits,
        )
    }
}
