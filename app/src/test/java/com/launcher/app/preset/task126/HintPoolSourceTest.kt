package com.launcher.app.preset.task126

import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
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
 * T043 — HintPoolSource (BundledHintPoolSource) Robolectric coverage
 * (FR-007, CL-7).
 *
 * Three scenarios:
 *  1. Valid JSON → parsed list
 *  2. Missing asset → empty list (no crash)
 *  3. Malformed JSON → empty list (no crash across domain boundary)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class HintPoolSourceTest {

    @Test
    fun load_readsBundledAsset_returnsEmptyOrPopulatedList() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = BundledHintPoolSource(ctx.assets)
        val hints = source.load()
        // The bundled seed ships an empty hints array; assertion is that no
        // exception is thrown and result is a valid (possibly empty) list.
        assertTrue("expected non-null list", hints.size >= 0)
    }

    @Test
    fun load_missingAsset_returnsEmptyList() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = BundledHintPoolSource(
            assets = ctx.assets,
            assetPath = "nonexistent-hint-pool.json",
        )
        val hints = source.load()
        assertEquals(emptyList<Any>(), hints)
    }

    @Test
    fun load_validJson_parsesEntries() = runTest {
        val json = """
            {
              "schemaVersion": "1.0", "minReaderVersion": "1.0", "minWriterVersion": "1.0",
              "hints": [
                {"hintId":"h1","targetComponentId":"font-tile","textKey":"hint.font"},
                {"hintId":"h2","targetComponentId":"sos-main","textKey":"hint.sos"}
              ]
            }
        """.trimIndent()
        val source = BundledHintPoolSource(
            assets = fakeAssetsWith("custom.json", json),
            assetPath = "custom.json",
        )
        val hints = source.load()
        assertEquals(2, hints.size)
        assertEquals("h1", hints[0].hintId)
        assertEquals("font-tile", hints[0].targetComponentId)
        assertEquals("hint.font", hints[0].textKey)
        assertEquals("h2", hints[1].hintId)
    }

    @Test
    fun load_malformedJson_returnsEmptyList() = runTest {
        val source = BundledHintPoolSource(
            assets = fakeAssetsWith("bad.json", "{ this is not json"),
            assetPath = "bad.json",
        )
        val hints = source.load()
        assertEquals(emptyList<Any>(), hints)
    }

    /** Builds a mocked AssetManager that returns [content] for a single [path]. */
    private fun fakeAssetsWith(path: String, content: String): AssetManager {
        val am = mockk<AssetManager>()
        every { am.open(path) } answers {
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
        }
        every { am.open(match { it != path }) } throws FileNotFoundException("test-fake asset")
        return am
    }
}
