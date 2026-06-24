package com.launcher.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.WizardEngine
import com.launcher.app.wizard.WizardActivity
import com.launcher.ui.senior.theme.SeniorWarmTheme
import org.koin.android.ext.android.inject

/**
 * Minimal Settings host activity for TASK-7 Phase 6 (FR-014 / FR-014a /
 * FR-017a). Bundles the three independent Composables introduced in
 * TASK-7:
 *  - [LocaleDivergenceIndicator] (Phase 3).
 *  - [PendingChecklistScreen] (Phase 6).
 *  - [WalkThroughButton] (Phase 6).
 *
 * The "Walk through" CTA launches [WizardActivity] with an extra so
 * `WizardActivity` knows to call `engine.runWalkThrough` instead of
 * `engine.run`. Final wiring of the extra → engine entry point is
 * documented as a Phase 6+ follow-up (the wizard host needs to pick
 * up the mode flag from intent extras).
 *
 * TODO(phase-6-ui-polish): senior-safe Settings entry — wire into
 * HomeActivity's overflow menu, add app-bar, theme switcher, etc.
 * Out of TASK-7 scope; this Activity stays minimal.
 */
class SettingsActivity : ComponentActivity() {

    private val engine: WizardEngine by inject()
    private val pendingVmDeps: PendingChecklistViewModel by inject()
    private val localeVmDeps: LocaleDivergenceViewModel by inject()
    private val stringResolver: StringResolver by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorWarmTheme.Light {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        var pendingState by remember {
                            mutableStateOf(PendingChecklistState(emptyList()))
                        }
                        var localeState by remember {
                            mutableStateOf(
                                LocaleDivergenceState(appLocale = "", systemLocale = ""),
                            )
                        }
                        LaunchedEffect(Unit) {
                            pendingState = pendingVmDeps.load()
                            localeState = localeVmDeps.state()
                        }
                        LocaleDivergenceIndicator(state = localeState, stringResolver = stringResolver)
                        PendingChecklistScreen(
                            state = pendingState,
                            stringResolver = stringResolver,
                        )
                        WalkThroughButton(
                            stringResolver = stringResolver,
                            onClick = ::launchWalkThrough,
                        )
                    }
                }
            }
        }
    }

    private fun launchWalkThrough() {
        startActivity(
            Intent(this, WizardActivity::class.java).apply {
                putExtra(WizardActivity.EXTRA_WALK_THROUGH, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
