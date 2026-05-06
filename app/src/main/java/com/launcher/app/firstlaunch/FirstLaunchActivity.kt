package com.launcher.app.firstlaunch

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.app.BuildConfig
import com.launcher.app.HomeActivity
import com.launcher.app.LauncherApplication
import com.launcher.app.R
import kotlinx.coroutines.launch

/**
 * First-launch picker. Skips itself if a preset is already selected.
 * Debug builds may pre-select via intent extra: --es preset <slug>.
 */
class FirstLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val core = (application as LauncherApplication).core

        lifecycleScope.launch {
            val existing = core.presetRepository.getActivePreset()
            if (existing != null) {
                proceedToHome()
                return@launch
            }

            // Debug-only override via intent extra
            if (BuildConfig.DEBUG) {
                val slug = intent.getStringExtra(EXTRA_PRESET)
                val byExtra = FlowPreset.fromSlug(slug)
                if (byExtra != null) {
                    core.presetRepository.setActivePreset(byExtra)
                    proceedToHome()
                    return@launch
                }
            }

            setContentView(R.layout.activity_first_launch)
            findViewById<View>(R.id.preset_card_workspace).setOnClickListener { pick(FlowPreset.WORKSPACE) }
            findViewById<View>(R.id.preset_card_launcher).setOnClickListener { pick(FlowPreset.LAUNCHER) }
            findViewById<View>(R.id.preset_card_simple_launcher).setOnClickListener { pick(FlowPreset.SIMPLE_LAUNCHER) }
        }
    }

    private fun pick(preset: FlowPreset) {
        val core = (application as LauncherApplication).core
        lifecycleScope.launch {
            core.presetRepository.setActivePreset(preset)
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
