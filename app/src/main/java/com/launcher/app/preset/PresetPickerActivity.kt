package com.launcher.app.preset

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.api.wizard.ConfigSource
import com.launcher.ui.PresetPickerScreen
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Standalone TASK-65 picker host (FR-012). Used by the boot router and
 * Settings "change preset" entry. Returns the chosen slug via result extra
 * `EXTRA_CHOSEN_SLUG`.
 *
 * Kept separate from the legacy `FirstLaunchActivity` to avoid coupling
 * preset selection to the F-5 setup wizard flow during TASK-65 rollout —
 * once preset composition replaces wizard, FirstLaunchActivity routes here.
 */
class PresetPickerActivity : ComponentActivity() {

    private val configSource: ConfigSource by inject()
    private val selectionService: PresetSelectionService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                PresetPickerScreen(
                    configSource = configSource,
                    onPick = { slug, _ ->
                        lifecycleScope.launch {
                            selectionService.beginSetup(slug)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(EXTRA_CHOSEN_SLUG, slug),
                            )
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CHOSEN_SLUG: String = "task65.chosen_slug"
    }
}
