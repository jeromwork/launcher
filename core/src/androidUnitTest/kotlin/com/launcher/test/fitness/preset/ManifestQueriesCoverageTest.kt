package com.launcher.test.fitness.preset

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * TASK-73 T073-027 (FR-010, plan.md §7 Risks). A missing `<queries><package>`
 * entry for an OEM package referenced by `vendor-recipes.json` silently breaks
 * `resolveActivity()` on Android 11+ — this fitness test fails CI loudly
 * instead, the moment a future recipe addition forgets the manifest entry.
 */
class ManifestQueriesCoverageTest {

    private val intentPackagePattern = Regex(""""intentPackage"\s*:\s*"([^"]+)"""")
    private val manifestPackagePattern = Regex("""<package\s+android:name="([^"]+)"\s*/>""")

    @Test
    fun everyVendorRecipeIntentPackage_hasManifestQueriesEntry() {
        val recipeFile = locate("app/src/main/assets/preset/vendor-recipes.json")
            ?: return fail("FR-010: vendor-recipes.json not found — the gate cannot verify anything, which is a defect, not a pass.")
        val manifestFile = locate("app/src/main/AndroidManifest.xml")
            ?: return fail("FR-010: AndroidManifest.xml not found — the gate cannot verify anything.")

        val referencedPackages = intentPackagePattern.findAll(recipeFile.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "Fixture drift: vendor-recipes.json no longer declares any intentPackage — this test guards nothing.",
            referencedPackages.isNotEmpty(),
        )

        val declaredInManifest = manifestPackagePattern.findAll(manifestFile.readText())
            .map { it.groupValues[1] }
            .toSet()

        val missing = referencedPackages - declaredInManifest
        assertTrue(
            "FR-010: every vendor-recipes.json intentPackage needs a matching " +
                "<queries><package android:name=\"...\"/> entry in AndroidManifest.xml.\n" +
                "Missing: $missing",
            missing.isEmpty(),
        )
    }

    private fun locate(relativePath: String): File? {
        val cwd = File(System.getProperty("user.dir") ?: return null)
        return listOf(File(cwd, "../$relativePath"), File(cwd, relativePath))
            .firstOrNull { it.exists() }
    }
}
