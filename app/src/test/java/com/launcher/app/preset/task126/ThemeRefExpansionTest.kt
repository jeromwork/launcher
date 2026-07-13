package com.launcher.app.preset.task126

import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.catalog.ThemeCatalog
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T044 — ThemeRef expansion coverage (FR-003, D3).
 *
 * Verifies:
 *  - `ThemeCatalog.resolve("dark")` (and other bundled names) → flat Theme fields.
 *  - Unknown name → `null`; callers must translate to a validation error before
 *    persistence (write-time ThemeRef expansion; runtime never sees a ThemeRef).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class ThemeRefExpansionTest {

    private lateinit var catalog: ThemeCatalog

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        catalog = ThemeCatalog(ctx)
    }

    @Test
    fun resolve_darkName_returnsFlatDarkTheme() {
        val theme = catalog.resolve("dark")
        assertNotNull("expected 'dark' to be present in bundled catalog", theme)
        theme!!
        assertEquals(true, theme.darkMode)
        assertEquals(TypographyScale.Medium, theme.typographyScale)
        assertEquals(ShapeStyle.Rounded, theme.shapeStyle)
        assertEquals("#6750A4", theme.paletteSeedHex)
    }

    @Test
    fun resolve_defaultName_returnsBrightTheme() {
        val theme = catalog.resolve("default")
        assertNotNull(theme)
        assertEquals(false, theme!!.darkMode)
    }

    @Test
    fun resolve_seniorLargeName_returnsLargeTypography() {
        val theme = catalog.resolve("senior-large")
        assertNotNull(theme)
        assertEquals(TypographyScale.ExtraLarge, theme!!.typographyScale)
    }

    @Test
    fun resolve_unknownName_returnsNull() {
        // T044 acceptance: unknown names surface as null so that the caller
        // can raise a ValidationError before persistence (never persist an
        // unresolved ThemeRef into a Preset wire payload).
        val theme = catalog.resolve("nonexistent-theme-name")
        assertNull(theme)
    }

    @Test
    fun availableNames_containsBundledEntries() {
        val names = catalog.availableNames()
        assertTrue("expected 'dark' in available names, got $names", "dark" in names)
        assertTrue("expected 'default' in available names, got $names", "default" in names)
    }
}
