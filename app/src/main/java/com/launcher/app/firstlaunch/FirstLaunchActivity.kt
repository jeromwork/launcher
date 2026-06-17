package com.launcher.app.firstlaunch

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.app.BuildConfig
import com.launcher.app.HomeActivity
import com.launcher.app.R
import com.launcher.app.setup.GmsHardBlockActivity
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.setup.PostNotificationsStep
import com.launcher.ui.setup.RoleHomeStep
import com.launcher.ui.setup.WizardProgressIndicator
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Spec 010 wizard host (T044 / T047) — extends the spec-3 preset picker with
 * three additional steps and a GMS-availability gate:
 *
 *   GMS check (T047) → preset picker → RoleHomeStep → PostNotificationsStep* → Home
 *
 *   (*) PostNotificationsStep is skipped automatically on API < 33 per FR-008.
 *
 * On entry: queries [GmsAvailabilityPort] (FR-042).
 *  - [GmsStatus.MissingFatal] → launch [GmsHardBlockActivity], finish (FR-042).
 *  - [GmsStatus.MissingRecoverable] → show system resolution dialog via
 *    `GoogleApiAvailability.getErrorDialog` (FR-044). User clicks «Update» →
 *    Play Store; returning here re-runs the check. **For now (Phase 3 v1)** —
 *    treated as the same path as Available: caller is expected to update GMS
 *    via the Play Store flow externally; full FR-044 resolution-dialog wiring
 *    is tracked as TODO[gms-recoverable-dialog] in the project backlog when
 *    the realBackend flavor surfaces it on actual devices.
 *  - [GmsStatus.Available] → proceed with the wizard.
 */
class FirstLaunchActivity : ComponentActivity() {

    private val presetRepository: PresetRepository by inject()
    private val gmsAvailability: GmsAvailabilityPort by inject()
    private val userPreferencesStore: com.launcher.api.wizard.UserPreferencesStore by inject()

    private var pickedPreset: FlowPreset? = null

    /**
     * Registered up-front per Activity Result contract requirements. Result
     * delivery does not block onCreate — the wizard step advances в callback
     * to next step regardless of grant/deny (skip-friendly per US-2 / US-4).
     */
    private val roleHomeRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Result is intentionally ignored — user can also pick a different
            // home launcher via system settings; the SetupCheck engine surfaces
            // the result later in Settings's `!N` badge.
            advanceAfterRoleHome()
        }

    private val notificationsRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Whether granted or denied, proceed to home — denial keeps a
            // Recommended SetupCheck open для later resolution from Settings.
            proceedToHome()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // T047 — GMS-availability gate at entry (FR-042).
            when (val status = gmsAvailability.status()) {
                GmsStatus.Available -> handlePresetAndProceed()
                is GmsStatus.MissingRecoverable -> {
                    // Phase 3 v1: treat recoverable same as Available; full
                    // FR-044 dialog wiring deferred — see kdoc.
                    handlePresetAndProceed()
                }
                is GmsStatus.MissingFatal -> {
                    startActivity(
                        Intent(this@FirstLaunchActivity, GmsHardBlockActivity::class.java),
                    )
                    finish()
                }
            }
        }
    }

    /** Skip the picker если preset уже выбран; otherwise render picker. */
    private suspend fun handlePresetAndProceed() {
        val existing = presetRepository.getActivePreset()
        if (existing != null) {
            pickedPreset = existing
            advanceAfterPreset()
            return
        }
        if (BuildConfig.DEBUG) {
            val slug = intent.getStringExtra(EXTRA_PRESET)
            val byExtra = FlowPreset.fromSlug(slug)
            if (byExtra != null) {
                presetRepository.setActivePreset(byExtra)
                pickedPreset = byExtra
                advanceAfterPreset()
                return
            }
        }
        renderPicker()
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
            pickedPreset = preset
            advanceAfterPreset()
        }
    }

    /**
     * After preset is chosen: show ROLE_HOME step (T042). Wizard progress «Шаг 2/M».
     * (Step 1 was the picker; Step M is `Home` itself implicitly — counted from
     * the user's perspective as: preset → role-home → notifications → done.)
     */
    private fun advanceAfterPreset() {
        val totalSteps = if (Build.VERSION.SDK_INT >= 33) 4 else 3
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                RoleHomeStep(
                    title = getString(R.string.setup_role_home_title),
                    body = getString(R.string.setup_role_home_body),
                    makeDefaultLabel = getString(R.string.setup_role_home_make_default),
                    skipLabel = getString(R.string.setup_role_home_skip),
                    onMakeDefault = ::requestRoleHome,
                    onSkip = ::advanceAfterRoleHome,
                    topContent = {
                        WizardProgressIndicator(
                            currentStep = 2,
                            totalSteps = totalSteps,
                            progressLabelTemplate = getString(R.string.setup_wizard_progress_step),
                        )
                    },
                )
            }
        }
    }

    /**
     * T045 — request ROLE_HOME with API-level fallback:
     *  - API ≥ 29: `RoleManager.createRequestRoleIntent(ROLE_HOME)`.
     *  - API 26-28: `Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_HOME)` —
     *    system chooser opens with our launcher in the list (plan §11 C-6).
     */
    private fun requestRoleHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_HOME)
            if (intent != null) {
                roleHomeRequest.launch(intent)
                return
            }
        }
        // API 26-28 legacy fallback — open the HOME chooser. The system shows
        // a picker dialog; user can either pick our launcher or one of the
        // pre-installed home apps. Either way, control returns here without
        // an explicit result.
        val chooser = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        roleHomeRequest.launch(chooser)
    }

    /** After ROLE_HOME step finishes (granted, denied, or skipped). */
    private fun advanceAfterRoleHome() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Show POST_NOTIFICATIONS step (T043).
            renderPostNotificationsStep()
        } else {
            proceedToHome()
        }
    }

    private fun renderPostNotificationsStep() {
        // If somehow already granted (re-entrant flow), skip the step.
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            proceedToHome()
            return
        }
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                PostNotificationsStep(
                    title = getString(R.string.setup_post_notifications_title),
                    body = getString(R.string.setup_post_notifications_body),
                    allowLabel = getString(R.string.setup_post_notifications_allow),
                    skipLabel = getString(R.string.setup_post_notifications_skip),
                    onAllow = {
                        // Build.VERSION.SDK_INT >= 33 guaranteed at this point.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            proceedToHome()
                        }
                    },
                    onSkip = ::proceedToHome,
                    topContent = {
                        WizardProgressIndicator(
                            currentStep = 3,
                            totalSteps = 4,
                            progressLabelTemplate = getString(R.string.setup_wizard_progress_step),
                        )
                    },
                )
            }
        }
    }

    private fun proceedToHome() {
        // Spec 015 (F-3) FR-005 — if the user-facing wizard has not run yet for
        // the active app-family, route to WizardActivity instead of HomeActivity.
        // Spec 010 setup wizard handles GMS + role-home + notifications; F-3
        // wizard handles language / theme / tile-set / system-settings detail.
        lifecycleScope.launch {
            val appFamilyId = "simple-launcher"
            val next = if (!userPreferencesStore.isWizardCompleted(appFamilyId)) {
                Intent(this@FirstLaunchActivity, com.launcher.app.wizard.WizardActivity::class.java)
            } else {
                Intent(this@FirstLaunchActivity, HomeActivity::class.java)
            }
            startActivity(next)
            finish()
        }
    }

    companion object {
        const val EXTRA_PRESET = "preset"
    }
}
