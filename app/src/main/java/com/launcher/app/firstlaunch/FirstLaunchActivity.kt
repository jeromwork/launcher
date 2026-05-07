package com.launcher.app.firstlaunch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.app.BuildConfig
import com.launcher.app.HomeActivity
import com.launcher.app.R
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * First-launch picker (US-306). Skips itself if a preset is already selected.
 * Debug builds may pre-select via intent extra: --es preset <slug>.
 *
 * UI is now [FirstLaunchScreen] from `:core/commonMain` (per ADR-005); the screen
 * itself is platform-agnostic and only the localized strings + activity wiring
 * stay here.
 */
class FirstLaunchActivity : ComponentActivity() {

    private val presetRepository: PresetRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val existing = presetRepository.getActivePreset()
            if (existing != null) {
                proceedToHome()
                return@launch
            }

            // Debug-only override via intent extra
            if (BuildConfig.DEBUG) {
                val slug = intent.getStringExtra(EXTRA_PRESET)
                val byExtra = FlowPreset.fromSlug(slug)
                if (byExtra != null) {
                    presetRepository.setActivePreset(byExtra)
                    proceedToHome()
                    return@launch
                }
            }

            renderPicker()
        }
    }

    private fun renderPicker() {
        val presets = listOf(
            uiModel(FlowPreset.WORKSPACE, R.string.preset_workspace_title, R.string.preset_workspace_description),
            uiModel(FlowPreset.LAUNCHER, R.string.preset_launcher_title, R.string.preset_launcher_description),
            uiModel(
                FlowPreset.SIMPLE_LAUNCHER,
                R.string.preset_simple_launcher_title,
                R.string.preset_simple_launcher_description,
            ),
        )
        setContent {
            LauncherTheme(preset = null) {
                FirstLaunchScreen(
                    presets = presets,
                    onPresetSelected = ::pick,
                )
            }
        }
    }

    private fun uiModel(preset: FlowPreset, titleRes: Int, descriptionRes: Int) =
        PresetUiModel(
            preset = preset,
            title = getString(titleRes),
            description = getString(descriptionRes),
        )

    private fun pick(preset: FlowPreset) {
        lifecycleScope.launch {
            presetRepository.setActivePreset(preset)
            proceedToHome()
        }
    }

    private fun proceedToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_PRESET = "preset"
    }
}
