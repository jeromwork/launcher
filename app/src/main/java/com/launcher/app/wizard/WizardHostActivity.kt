package com.launcher.app.wizard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.launcher.api.localization.StringResolver
import com.launcher.app.HomeActivity
import com.launcher.app.firstlaunch.FirstLaunchActivity
import com.launcher.preset.engine.ReconcileState
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * TASK-126 T054 / T055 — host for the new preset-composition wizard.
 *
 * Replaces the legacy [WizardActivity] (spec 015) as the destination
 * [FirstLaunchActivity.proceedToHome] navigates to after the Sign-In +
 * recovery-passphrase steps. The legacy [WizardActivity] path — and the
 * whole `WizardEngineImpl` graph behind it — is scheduled for deletion
 * in Phase 7 (T103 / T108).
 *
 * T054 (SplashScreen API): the activity theme is `Theme.Launcher.WizardSplash`
 * (`parent="Theme.SplashScreen"`, `postSplashScreenTheme` = the normal app
 * theme). We `installSplashScreen()` inside `onCreate` **before** `super`
 * and hold it via `setKeepOnScreenCondition` until `WizardViewModel.state`
 * emits its first non-Loading value. This hides the black flash while
 * `PresetBootstrap` reads bundled `pool.json` / `preset.json` from assets.
 *
 * T053 Denial UX: on `ReconcileState.Denied` the [WizardScreen] "try
 * another preset" button routes back to [FirstLaunchActivity] with
 * `FLAG_ACTIVITY_CLEAR_TASK`, forcing the picker to re-run so the user can
 * choose a preset that does not require the declined component.
 */
class WizardHostActivity : ComponentActivity() {

    private val viewModel: WizardViewModel by inject()
    private val stringResolver: StringResolver by inject()
    private val postWizardKioskApply: PostWizardKioskApply by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash until the ViewModel leaves Idle/Loading — i.e. bootstrap
        // finished and the engine either reached the first Interactive step,
        // completed synchronously (Done), or failed (Failed / Denied).
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        lifecycleScope.launch {
            viewModel.state
                .filter { it !is ReconcileState.Idle && it !is ReconcileState.Loading }
                .first()
            keepSplash = false
        }

        setContent {
            LauncherTheme(preset = null) {
                WizardScreen(
                    viewModel = viewModel,
                    stringResolver = stringResolver,
                    onCompleted = { profile ->
                        // T056 — apply StatusBarPolicy + LauncherRole exactly
                        // once, at the wizard→home transition. Mid-wizard the
                        // engine intentionally skips them (would disorient a
                        // senior user with a system dialog or hidden bar).
                        lifecycleScope.launch {
                            runCatching { postWizardKioskApply.applyKiosk(profile) }
                                .onFailure {
                                    android.util.Log.w(
                                        "WizardHost",
                                        "post-wizard kiosk apply failed",
                                        it,
                                    )
                                }
                            startActivity(
                                Intent(this@WizardHostActivity, HomeActivity::class.java)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                    ),
                            )
                            finish()
                        }
                    },
                    onPickAnotherPreset = {
                        viewModel.reset()
                        startActivity(
                            Intent(this@WizardHostActivity, FirstLaunchActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                ),
                        )
                        finish()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
