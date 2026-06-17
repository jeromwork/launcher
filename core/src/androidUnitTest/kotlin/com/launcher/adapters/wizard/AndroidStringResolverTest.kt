package com.launcher.adapters.wizard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Verifies the dot/hyphen → underscore normalisation done by
 * [AndroidStringResolver]. Real Android resource names cannot contain dots;
 * commonMain callers use dotted keys for readability and the adapter
 * translates at lookup time.
 *
 * Uses Robolectric so we get a real Android Resources implementation backed
 * by the same XML files the app actually ships.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidStringResolverTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val resolver = AndroidStringResolver(ctx)

    @Test
    fun simpleKey_resolvesFromBaseXml() {
        // strings_wizard.xml has `wizard_next` = "Next" in base values/.
        assertEquals("Next", resolver.resolve("wizard_next"))
    }

    @Test
    fun dottedKey_isNormalised() {
        // Caller writes the dotted form; adapter normalises to wizard_next.
        assertEquals("Next", resolver.resolve("wizard.next"))
    }

    @Test
    fun hyphenInKey_isNormalised() {
        // We do not have a hyphenated string in base, but the conversion logic
        // is symmetric — write through the same code path with a verified key.
        assertEquals("Next", resolver.resolve("wizard-next"))
    }

    @Test
    fun camelCaseInKey_isPreserved() {
        // `ui_tileSet_question` keeps camelCase in the XML; adapter must NOT
        // split it into `ui_tile_set_question`.
        val v = resolver.resolve("ui.tileSet.question")
        assertEquals("Choose starter tile set", v)
    }

    @Test
    fun missingKey_returnsLiteralKey() {
        val key = "definitely.does.not.exist.anywhere"
        assertEquals(key, resolver.resolve(key))
    }

    @Test
    fun argInterpolation_appliesAfterLookup() {
        // wizard_step_n_of_m = "Step {current} of {total}"
        val out = resolver.resolve(
            "wizard.step_n_of_m",
            mapOf("current" to 3, "total" to 12),
        )
        assertEquals("Step 3 of 12", out)
    }

    @Test
    fun systemSettingDotKeyWithUnderscoreId_resolves() {
        // android.role.home → system_setting_android_role_home_label
        assertEquals(
            "Set as default launcher",
            resolver.resolve("system_setting.android.role.home.label"),
        )
    }

    @Test
    fun systemSettingWithUppercasePermissionName_resolves() {
        // android.permission.POST_NOTIFICATIONS keeps the uppercase fragment
        // — Android res names ARE case-sensitive identifiers; legal here.
        assertEquals(
            "Allow notifications",
            resolver.resolve("system_setting.android.permission.POST_NOTIFICATIONS.label"),
        )
    }
}
