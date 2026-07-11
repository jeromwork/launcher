package com.launcher.app.preset.task126

import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.Task120TestApplication
import com.launcher.app.preset.task120.adapter.BundledPoolSource
import com.launcher.app.preset.task120.adapter.BundledPresetSource
import com.launcher.preset.engine.PresetValidationResult
import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.port.CapabilityContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * T027 (FR-019, SC-12, CL-8) — Bundled preset validation.
 *
 * Iterates every JSON under `app/src/main/assets/preset/bundled-presets/` and asserts
 * [PresetValidator.validateToResult] returns [PresetValidationResult.Success] against the
 * bundled Pool. Guards that ship-time assets never regress into an invalid state.
 *
 * Depends on T020 (validateToResult) and T015/T017 (v2 schema) — both committed in
 * the T010–T020 domain commit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Task120TestApplication::class)
class BundledPresetValidationTest {

    private val emptyContract = object : CapabilityContract {
        override fun requires(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
        override fun provides(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
    }

    @Test
    fun allBundledPresets_validateToSuccess() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pool = BundledPoolSource(ctx).loadPool()
        val source = BundledPresetSource(ctx)
        val available = source.listAvailable()

        assertTrue(
            "expected at least one bundled preset under assets/preset/bundled-presets/, got $available",
            available.isNotEmpty(),
        )

        val validator = PresetValidator(emptyContract)
        for (id in available) {
            val preset = source.loadPreset(id)
                ?: fail("Missing bundled preset '$id'").let { return@runTest }
            when (val r = validator.validateToResult(preset, pool)) {
                is PresetValidationResult.Success -> {
                    // ok
                }
                is PresetValidationResult.Failure -> {
                    fail("Bundled preset '$id' failed validation: ${r.errors}")
                }
            }
        }
    }
}
