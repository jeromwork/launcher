package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-136 T136-037 (SC-011, NFR-003, FR-012) + T136-038 (SC-008, FR-015b, NFR-001)
 * — fitness gates for the own ECS core.
 *
 *  - The `preset/ecs/` package stays ≤ ~400 LOC (guards against regressing to a
 *    game runtime — Risk R-3).
 *  - The `TODO(ecs-fleks-migration)` swap seam is present on `World.kt`.
 *  - `Entity` / `Component` / `Blueprint` / `LifecycleState` and the whole
 *    `preset/ecs/` core carry zero `import android.*` / vendor SDK imports (rule 1).
 */
class EcsCoreFitnessTest {

    private val commonMain by lazy { locate("src/commonMain/kotlin/com/launcher/preset") }

    private fun locate(rel: String): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, rel), File(cwd, "core/$rel")).firstOrNull { it.isDirectory }
            ?: error("dir not found from cwd=$cwd: $rel")
    }

    @Test
    fun ecsPackage_isUnderLocBudget() {
        val ecsDir = File(commonMain, "ecs")
        val loc = ecsDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { file ->
                file.readLines().count { line ->
                    val t = line.trim()
                    t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
                }
            }
        assertTrue(
            "preset/ecs is $loc LOC (budget ≤ 400) — the own core must stay a small " +
                "Fleks-shaped vocabulary, not a game runtime (NFR-003).",
            loc <= 400,
        )
    }

    @Test
    fun worldFile_carriesFleksMigrationSeam() {
        val world = File(commonMain, "ecs/World.kt")
        assertTrue("World.kt missing", world.isFile)
        assertTrue(
            "World.kt must carry the TODO(ecs-fleks-migration) swap seam (FR-012).",
            world.readText().contains("TODO(ecs-fleks-migration)"),
        )
    }

    @Test
    fun modelAndEcsCore_haveNoAndroidOrVendorImports() {
        val guardedFiles = listOf(
            File(commonMain, "model/Entity.kt"), // may not exist as its own file
            File(commonMain, "model/Profile.kt"),
            File(commonMain, "model/Component.kt"),
            File(commonMain, "model/Pool.kt"),
            File(commonMain, "model/LifecycleState.kt"),
        ).filter { it.isFile } + File(commonMain, "ecs").walkTopDown().filter {
            it.isFile && it.extension == "kt"
        }

        val violations = guardedFiles.flatMap { file ->
            file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("import ") }
                .filter { imp ->
                    imp.contains("android") ||
                        imp.contains("androidx") ||
                        imp.contains("com.google")
                }
                .map { "${file.name}: $it" }
        }
        assertTrue(
            "Domain / ecs core must have zero Android/vendor imports (rule 1):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }
}
