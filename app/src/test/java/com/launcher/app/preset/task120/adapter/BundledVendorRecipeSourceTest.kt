package com.launcher.app.preset.task120.adapter

import family.wire.WireVersion

import android.content.Context
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.Task120TestApplication
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * TASK-73 T073-013/014 (contracts/vendor-recipe-catalogue.md "Read semantics",
 * "Tests"). Missing asset and unsupported-`schemaVersion` both resolve to an
 * empty [com.launcher.preset.model.VendorRecipeCatalogue] rather than throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Task120TestApplication::class)
class BundledVendorRecipeSourceTest {

    @Test
    fun missingAsset_returnsEmptyCatalogue_noException() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val source = BundledVendorRecipeSource(ctx, assetPath = "preset/nonexistent-vendor-recipes.json")

        val catalogue = source.loadCatalogue()

        assertEquals(WireVersion(1, 0), catalogue.schemaVersion)
        assertTrue(catalogue.entries.isEmpty())
    }

    @Test
    fun unsupportedSchemaVersion_returnsEmptyCatalogue_noException() = runTest {
        // Newer writer AND a raised reader minimum — under §3 the version alone no longer refuses,
        // so this fixture has to state that it genuinely needs a reader we do not have.
        val json = """
            {
              "schemaVersion": "99.0", "minReaderVersion": "99.0", "minWriterVersion": "99.0",
              "entries": {
                "LauncherRole": {
                  "Xiaomi": { "fallbackTextKey": "launcher_role.fallback.xiaomi" }
                }
              }
            }
        """.trimIndent()
        val source = BundledVendorRecipeSource(
            context = fakeContextWith("vendor-recipes.json", json),
            assetPath = "vendor-recipes.json",
        )

        val catalogue = source.loadCatalogue()

        assertTrue(catalogue.entries.isEmpty())
    }

    @Test
    fun malformedJson_returnsEmptyCatalogue_noException() = runTest {
        val source = BundledVendorRecipeSource(
            context = fakeContextWith("vendor-recipes.json", "{ this is not json"),
            assetPath = "vendor-recipes.json",
        )

        val catalogue = source.loadCatalogue()

        assertTrue(catalogue.entries.isEmpty())
    }

    @Test
    fun validJson_parsesAndFiltersToKnownComponentTypes() = runTest {
        val json = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "entries": {
                "LauncherRole": {
                  "Xiaomi": { "fallbackTextKey": "launcher_role.fallback.xiaomi" },
                  "Oppo": { "fallbackTextKey": "launcher_role.fallback.oppo" }
                },
                "SomeFutureComponent": {
                  "Xiaomi": { "fallbackTextKey": "future.fallback.xiaomi" }
                }
              }
            }
        """.trimIndent()
        val source = BundledVendorRecipeSource(
            context = fakeContextWith("vendor-recipes.json", json),
            assetPath = "vendor-recipes.json",
        )

        val catalogue = source.loadCatalogue()

        assertEquals(setOf("LauncherRole"), catalogue.entries.keys)
        assertEquals(setOf("Xiaomi"), catalogue.entries.getValue("LauncherRole").keys)
    }

    /** Mocked Context whose AssetManager returns [content] for a single [path]. */
    private fun fakeContextWith(path: String, content: String): Context {
        val am = mockk<AssetManager>()
        every { am.open(path) } answers {
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }
        every { am.open(match { it != path }) } throws FileNotFoundException("test-fake asset")
        val context = mockk<Context>()
        every { context.assets } returns am
        return context
    }
}
