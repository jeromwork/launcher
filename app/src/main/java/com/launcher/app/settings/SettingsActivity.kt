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
import com.launcher.app.wizard.WizardHostActivity
import com.launcher.ui.senior.theme.SeniorWarmTheme
import org.koin.android.ext.android.inject

/**
 * Minimal Settings host activity (FR-014 / FR-014a / FR-017a).
 * Bundles:
 *  - [LocaleDivergenceIndicator]
 *  - [PendingChecklistScreen]
 *  - [WalkThroughButton] — TASK-126 rewire: opens the new preset-composition
 *    wizard host so the user can re-walk the flow. The legacy F-3
 *    `WizardEngine.runWalkThrough` mode does not exist in the ECS runtime;
 *    ReconcileEngine re-derives progress from `Provider.check()` on every
 *    run, so a fresh entry into `WizardHostActivity` skips already-Applied
 *    components and re-prompts for the rest.
 */
class SettingsActivity : ComponentActivity() {

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
            Intent(this, WizardHostActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
