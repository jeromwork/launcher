package com.launcher.app.preset.task126

import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.facade.DataStoreAppThemeController
import com.launcher.app.preset.task120.provider.ThemeProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ShapeStyle
import com.launcher.preset.model.TypographyScale
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T040 — ThemeProvider Robolectric coverage (FR-003, US-1).
 *
 * - apply() writes DataStore via [DataStoreAppThemeController]
 * - check() returns Ok when persisted state matches the requested theme,
 *   NeedsApply otherwise
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class ThemeProviderTest {

    private lateinit var provider: ThemeProvider
    private lateinit var controller: DataStoreAppThemeController
    private lateinit var profile: Profile

    private val targetTheme = Component.Theme(
        paletteSeedHex = "#0B6BCB",
        typographyScale = TypographyScale.Large,
        shapeStyle = ShapeStyle.Rounded,
        darkMode = false,
    )

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        controller = DataStoreAppThemeController(ctx)
        provider = ThemeProvider(controller)
        profile = Profile(basedOnPreset = "test", presetVersion = 1, layoutKey = "layout.default")
        // DataStore is a JVM-scoped singleton keyed by name; between tests the
        // in-memory cache survives. Reset by writing a sentinel "empty" Theme
        // — but since Theme has no "cleared" state, we simply write a known
        // baseline that our tests do not accidentally match against.
        // (We do not rely on this baseline in assertions.)
        val sentinel = Component.Theme(
            paletteSeedHex = "#000000",
            typographyScale = TypographyScale.Small,
            shapeStyle = ShapeStyle.Sharp,
            darkMode = false,
        )
        controller.set(sentinel)
        // Delete the on-disk file so a subsequent JVM restart is also clean.
        File(ctx.filesDir, "datastore/task126_theme_prefs.preferences_pb").delete()
        Unit
    }

    @After
    fun tearDown() = kotlinx.coroutines.runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(ctx.filesDir, "datastore/task126_theme_prefs.preferences_pb").delete()
        Unit
    }

    @Test
    fun check_returnsNeedsApply_whenPersistedStateDoesNotMatchTarget() = runTest {
        // setUp() persists a sentinel Theme that differs from targetTheme.
        val outcome = provider.check(targetTheme, profile)
        assertEquals(Outcome.NeedsApply, outcome)
    }

    @Test
    fun apply_writesDataStore_thenCheckReturnsOk() = runTest {
        val applyOutcome = provider.apply(targetTheme, profile)
        assertEquals(Outcome.Ok, applyOutcome)

        val current = controller.current()
        assertEquals(targetTheme, current)

        val checkOutcome = provider.check(targetTheme, profile)
        assertEquals(Outcome.Ok, checkOutcome)
    }

    @Test
    fun check_returnsNeedsApply_whenPersistedThemeDiffers() = runTest {
        provider.apply(targetTheme, profile)
        val other = targetTheme.copy(darkMode = true)
        val outcome = provider.check(other, profile)
        assertEquals(Outcome.NeedsApply, outcome)
    }
}
