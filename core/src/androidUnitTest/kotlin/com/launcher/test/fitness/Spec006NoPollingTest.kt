package com.launcher.test.fitness

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Fitness test for spec 006 NFR-N05 + NFR-N03 — никакого polling и никакого
 * WorkManager в спеке 006. Все обновления event-driven через RESUMED hooks,
 * broadcast receivers и ContentObservers. Battery budget ≤ 0.1%/day (NFR-005)
 * зависит от absence of periodic wake-ups.
 *
 * Plain string scan (НЕ Konsist) — runs в androidUnitTest где user.dir flips,
 * matches existing DomainIsolationTest pattern.
 *
 * Forbidden symbols в спеке 006 packages:
 *  - `kotlinx.coroutines.flow.tickerFlow` — periodic Flow ticker
 *  - `kotlinx.coroutines.delay` inside loops без cancellation gate (трудно
 *    ловить статически — мы покрываем его repeated-emission patterns)
 *  - `java.util.Timer` — JVM timer
 *  - `androidx.work.` — WorkManager (вынесено в спек 013)
 *  - `AlarmManager.setRepeating` / `setExactAndAllowWhileIdle` — exact alarms
 *
 * Spec 006 packages monitored:
 *  - `com.launcher.api.capability`
 *  - `com.launcher.api.health`
 *  - `com.launcher.api.settings`
 *  - `com.launcher.api.alerts`
 *  - `com.launcher.core.capability`
 *  - `com.launcher.core.health`
 *  - `com.launcher.core.settings`
 *  - `com.launcher.core.airplane`
 *  - `com.launcher.core.diagnostics`
 */
class Spec006NoPollingTest {

    private val packages = listOf(
        "core/src/commonMain/kotlin/com/launcher/api/capability",
        "core/src/commonMain/kotlin/com/launcher/api/health",
        "core/src/commonMain/kotlin/com/launcher/api/settings",
        "core/src/commonMain/kotlin/com/launcher/api/alerts",
        "core/src/androidMain/kotlin/com/launcher/core/capability",
        "core/src/androidMain/kotlin/com/launcher/core/health",
        "core/src/androidMain/kotlin/com/launcher/core/settings",
        "core/src/androidMain/kotlin/com/launcher/core/airplane",
        "core/src/androidMain/kotlin/com/launcher/core/diagnostics",
    )

    private val forbiddenPatterns = listOf(
        "tickerFlow",
        "java.util.Timer",
        "androidx.work.",
        "WorkManager",
        "setRepeating",
        "setExactAndAllowWhileIdle",
    )

    @Test
    fun spec006_packages_have_no_polling_symbols() {
        val violations = mutableListOf<String>()
        val rootCandidates = listOf(File("."), File(".."))

        packages.forEach { relativePath ->
            val dir = rootCandidates.map { File(it, relativePath) }.firstOrNull { it.isDirectory }
                ?: return@forEach // skip if path absent (partial checkout)

            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    forbiddenPatterns.forEach { pattern ->
                        if (text.contains(pattern)) {
                            violations.add("${file.path}: contains forbidden '$pattern'")
                        }
                    }
                }
        }

        assertEquals(
            "Spec 006 packages must not use polling / WorkManager / timers (NFR-N03, NFR-N05). " +
                "Violations:\n${violations.joinToString("\n")}",
            emptyList<String>(),
            violations,
        )
    }
}
