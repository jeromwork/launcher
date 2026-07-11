package com.launcher.app.preset.task126

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.launcher.app.preset.task120.provider.LanguageProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T041 — LanguageProvider Robolectric coverage (FR-004, FR-022, SC-11).
 *
 * Verifies:
 *  - apply() calls [AppCompatDelegate.setApplicationLocales] with the requested locale
 *  - sentinel "system" produces an empty [LocaleListCompat]
 *  - check() reports Ok when the current locale matches, NeedsApply otherwise
 *
 * Uses `mockkStatic` on AppCompatDelegate because Robolectric's application-locale
 * bookkeeping is asynchronous and unreliable in unit tests; intercepting the static
 * method gives us a deterministic assertion surface.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class LanguageProviderTest {

    private lateinit var provider: LanguageProvider
    private lateinit var profile: Profile
    private var currentLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()

    @Before
    fun setUp() {
        provider = LanguageProvider()
        profile = Profile(basedOnPreset = "test", presetVersion = 1, layoutKey = "layout.default")
        currentLocales = LocaleListCompat.getEmptyLocaleList()

        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.getApplicationLocales() } answers { currentLocales }
        every { AppCompatDelegate.setApplicationLocales(any()) } answers {
            currentLocales = firstArg()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(AppCompatDelegate::class)
    }

    @Test
    fun apply_callsSetApplicationLocalesWithExplicitLocale() = runTest {
        val slot = slot<LocaleListCompat>()
        every { AppCompatDelegate.setApplicationLocales(capture(slot)) } answers {
            currentLocales = slot.captured
        }

        val outcome = provider.apply(Component.Language(locale = "ru"), profile)

        assertEquals(Outcome.Ok, outcome)
        verify(exactly = 1) { AppCompatDelegate.setApplicationLocales(any()) }
        assertEquals(LocaleListCompat.forLanguageTags("ru"), slot.captured)
    }

    @Test
    fun apply_systemSentinel_producesEmptyLocaleList() = runTest {
        // Pre-set an explicit locale to prove sentinel resets to system.
        currentLocales = LocaleListCompat.forLanguageTags("de")

        val slot = slot<LocaleListCompat>()
        every { AppCompatDelegate.setApplicationLocales(capture(slot)) } answers {
            currentLocales = slot.captured
        }

        val outcome = provider.apply(Component.Language(locale = "system"), profile)

        assertEquals(Outcome.Ok, outcome)
        verify(exactly = 1) { AppCompatDelegate.setApplicationLocales(any()) }
        assertTrue(
            "expected empty LocaleList for 'system' sentinel, got ${slot.captured}",
            slot.captured.isEmpty,
        )
    }

    @Test
    fun check_returnsOk_whenAppliedLocaleMatches() = runTest {
        currentLocales = LocaleListCompat.forLanguageTags("en")
        val outcome = provider.check(Component.Language(locale = "en"), profile)
        assertEquals(Outcome.Ok, outcome)
    }

    @Test
    fun check_returnsNeedsApply_whenAppliedLocaleDiffers() = runTest {
        currentLocales = LocaleListCompat.forLanguageTags("en")
        val outcome = provider.check(Component.Language(locale = "ru"), profile)
        assertEquals(Outcome.NeedsApply, outcome)
    }
}
