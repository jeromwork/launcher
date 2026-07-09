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
import com.launcher.api.auth.AuthProvider
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.app.BuildConfig
import com.launcher.app.HomeActivity
import com.launcher.app.R
import com.launcher.app.setup.GmsHardBlockActivity
import com.launcher.app.ui.recovery.RecoveryFallbackScreen
import com.launcher.app.ui.recovery.RecoveryPassphraseEntryScreen
import com.launcher.app.ui.recovery.RecoveryPassphraseSetupScreen
import com.launcher.app.ui.recovery.RecoveryProbeErrorScreen
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.setup.AuthChoiceStep
import com.launcher.ui.setup.PostNotificationsStep
import com.launcher.ui.setup.RoleHomeStep
import com.launcher.ui.setup.WizardProgressIndicator
import com.launcher.ui.theme.LauncherTheme
import cryptokit.crypto.api.AeadCipher
import cryptokit.crypto.api.RandomSource
import family.keys.api.IdentityProof
import family.keys.api.BackupError
import family.keys.api.Outcome
import family.keys.api.PassphraseAttemptCounter
import family.keys.api.PassphrasePrompter
import family.keys.api.RecoveryError
import family.keys.api.RecoveryKeyBackup
import family.keys.api.RootKey
import family.keys.impl.Argon2idPassphraseKdf
import family.keys.impl.RecoveryFlow
import family.keys.impl.RootKeyManagerImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.firstOrNull
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
    private val authProvider: AuthProvider by inject()

    // task-6 wiring 2026-06-30: dependencies for the F-5 Setup-passphrase step
    // that runs once between Sign-In success and ROLE_HOME prompt.
    private val rootKeyManager: RootKeyManagerImpl by inject()
    private val identityProof: IdentityProof by inject()
    private val recoveryKeyBackup: RecoveryKeyBackup by inject()
    private val argon2idKdf: Argon2idPassphraseKdf by inject()
    private val aead: AeadCipher by inject()
    private val random: RandomSource by inject()
    private val attemptCounter: PassphraseAttemptCounter by inject()

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
     * After preset is chosen: show **F-4 auth choice step** (spec 017 US 2).
     * Flow: preset → auth-choice → role-home → notifications → done.
     */
    private fun advanceAfterPreset() {
        renderAuthChoiceStep()
    }

    /**
     * Spec 017 (F-4 AuthProvider) US 2 — auth choice step. Два варианта:
     *  - «Настроить с нуля» (skip) → продолжить wizard.
     *  - «Войти в Google» → Credential Manager bottom-sheet → success → продолжить.
     *
     * После любого исхода переход к [renderRoleHomeStep].
     */
    private fun renderAuthChoiceStep() {
        val totalSteps = if (Build.VERSION.SDK_INT >= 33) 5 else 4
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                AuthChoiceStep(
                    authProvider = authProvider,
                    onSkip = ::renderRoleHomeStep,
                    onSignedIn = ::renderRecoveryBranchStep,
                    onBack = ::renderPicker,
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
     * task-119 wiring 2026-07-08: branch between Setup and Entry based on
     * whether a recovery blob already exists for this identity.
     *
     * Runs ONLY on the signed-in branch (`AuthChoiceStep.onSignedIn`). The
     * skip-Sign-In path bypasses recovery entirely — there's no identity to
     * back up or restore, so [renderRoleHomeStep] is the next step there.
     *
     * Logic:
     *  1. Read current identity (Firebase sub → stableId).
     *  2. Probe `recoveryKeyBackup.fetchBlob(stableId)`.
     *  3. Success (blob present) → render Entry screen (existing user recovering).
     *  4. NotFound (404) → render Setup screen (first-time user creating backup).
     *  5. Any other error (AuthExpired / NetworkUnavailable / Malformed / …) →
     *     render **probe-error screen** (safety brake). Overwriting a blob is
     *     one-way; without an explicit 404 we cannot prove the user is new,
     *     so we must not silently fall through to Setup.
     *
     * Without this branch (pre-task-119 behavior), the wizard always showed
     * Setup — a returning user on a new device would overwrite their existing
     * cloud blob and lose access to all previously encrypted data.
     */
    private fun renderRecoveryBranchStep() {
        lifecycleScope.launch {
            val identity = identityProof.identityFlow.firstOrNull()
            if (identity == null) {
                android.util.Log.w(
                    "F5Branch",
                    "no identity after Sign-In — showing probe-error safety brake",
                )
                renderRecoveryProbeErrorStep()
                return@launch
            }
            // TASK-119 (2026-07-09): single fetchBlob call. Our JWT (issued by
            // auth-worker /auth/exchange) carries stableId in `sub` synchronously
            // — no propagation race, no client-side retry needed to survive
            // Firebase custom-claim timing. Retry within WorkerRecoveryKeyBackup
            // covers only transient transport (5xx / 429 / IOException).
            when (val probe = recoveryKeyBackup.fetchBlob(identity.stableId)) {
                is Outcome.Success -> {
                    android.util.Log.i(
                        "F5Branch",
                        "existing recovery blob found — Entry",
                    )
                    renderRecoveryEntryStep(failedAttempts = 0)
                }
                is Outcome.Failure -> when (probe.error) {
                    BackupError.NotFound -> {
                        android.util.Log.i("F5Branch", "no recovery blob — Setup")
                        renderRecoverySetupStep()
                    }
                    else -> {
                        android.util.Log.w(
                            "F5Branch",
                            "fetchBlob failed with ${probe.error} — probe-error safety brake",
                        )
                        renderRecoveryProbeErrorStep()
                    }
                }
            }
        }
    }

    /**
     * task-119 safety brake — see [RecoveryProbeErrorScreen] kdoc for rationale.
     * Silent fall-through to Setup on non-404 probe errors risks overwriting a
     * returning user's blob; require an explicit choice instead.
     */
    private fun renderRecoveryProbeErrorStep() {
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                RecoveryProbeErrorScreen(
                    onRetry = ::renderRecoveryBranchStep,
                    onSetupAnyway = ::renderRecoverySetupStep,
                    onSkip = ::renderRoleHomeStep,
                )
            }
        }
    }

    /**
     * task-119 wiring 2026-07-08: F-5 recovery-passphrase Entry step.
     *
     * Reached when [renderRecoveryBranchStep] detected an existing blob for
     * this identity. User enters the passphrase they set on the original
     * device; [RecoveryFlow.performRecovery] fetches + unwraps → seeds
     * [RootKeyManagerImpl] → wizard advances to ROLE_HOME.
     *
     * On wrong passphrase [PassphraseAttemptCounter] increments; UI re-renders
     * with updated `failedAttempts`. On 5th wrong attempt →
     * [renderRecoveryFallbackStep] (per SC-002; matches spec FR-015 counter
     * semantics — note the on-screen "осталось попыток" copy in
     * [RecoveryPassphraseEntryScreen] uses a 3-attempt display convention
     * that predates the SC-002 5-attempt smoke; alignment is a follow-up
     * task, not a task-119 blocker).
     */
    private fun renderRecoveryEntryStep(failedAttempts: Int) {
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                RecoveryPassphraseEntryScreen(
                    failedAttempts = failedAttempts,
                    onSubmit = { passphrase ->
                        lifecycleScope.launch {
                            runRecoveryEntry(passphrase, failedAttempts)
                        }
                    },
                    onCancel = ::renderRoleHomeStep,
                    onFallback = { renderRecoveryFallbackStep(FALLBACK_REASON_TOO_MANY) },
                )
            }
        }
    }

    private suspend fun runRecoveryEntry(passphrase: CharArray, currentFailed: Int) {
        val identity = identityProof.identityFlow.firstOrNull()
        if (identity == null) {
            android.util.Log.w("F5Entry", "no identity at entry — advancing wizard")
            passphrase.fill(' ')
            renderRoleHomeStep()
            return
        }
        val flow = RecoveryFlow(
            rootKeyManager = rootKeyManager,
            backup = recoveryKeyBackup,
            kdf = argon2idKdf,
            aead = aead,
            random = random,
            prompter = OneShotRecoveryPrompter(passphrase),
            attemptCounter = attemptCounter,
        )
        when (val r = flow.performRecovery(identity)) {
            is Outcome.Success -> {
                android.util.Log.i("F5Entry", "recovery succeeded for stableId=${identity.stableId}")
                renderRoleHomeStep()
            }
            is Outcome.Failure -> when (r.error) {
                RecoveryError.WrongPassphrase -> renderRecoveryEntryStep(currentFailed + 1)
                RecoveryError.TooManyAttempts -> renderRecoveryFallbackStep(FALLBACK_REASON_TOO_MANY)
                RecoveryError.NoVaultPresent -> {
                    android.util.Log.w("F5Entry", "vault vanished between probe and unwrap — Setup")
                    renderRecoverySetupStep()
                }
                RecoveryError.MalformedVault -> renderRecoveryFallbackStep(FALLBACK_REASON_MALFORMED)
                RecoveryError.Cancelled -> renderRoleHomeStep()
            }
        }
    }

    private fun renderRecoveryFallbackStep(reason: com.launcher.app.ui.recovery.RecoveryViewModel.FallbackReason) {
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                RecoveryFallbackScreen(
                    reason = reason,
                    onSetupAsNewDevice = ::renderRecoverySetupStep,
                    onRetry = { renderRecoveryEntryStep(failedAttempts = 0) },
                )
            }
        }
    }

    /**
     * task-6 wiring 2026-06-30: F-5 recovery-passphrase Setup step.
     *
     * Runs on the signed-in branch when no existing recovery blob was found
     * (see [renderRecoveryBranchStep]).
     *
     * On submit:
     *  1. Get or generate the root key via [RootKeyManagerImpl.getOrCreate].
     *  2. Construct an ad-hoc [RecoveryFlow] with a one-shot
     *     [PassphrasePrompter] that returns the just-entered CharArray.
     *  3. `performSetup` derives the Argon2id wrap key, AEAD-wraps the root,
     *     uploads the blob via [RecoveryKeyBackup] (= WorkerRecoveryKeyBackup
     *     in realBackend) and returns.
     *  4. Advance to ROLE_HOME regardless of upload outcome — we log failure
     *     but do not block the wizard; the user can retry from Settings later
     *     (deferred-flag pattern per FR-014).
     */
    private fun renderRecoverySetupStep() {
        setContent {
            LauncherTheme(preset = pickedPreset?.slug) {
                RecoveryPassphraseSetupScreen(
                    onSubmit = { passphrase ->
                        lifecycleScope.launch {
                            runRecoverySetup(passphrase)
                            renderRoleHomeStep()
                        }
                    },
                    onCancel = ::renderRoleHomeStep,
                )
            }
        }
    }

    private suspend fun runRecoverySetup(passphrase: CharArray) {
        val identity = identityProof.identityFlow.firstOrNull()
        if (identity == null) {
            android.util.Log.w(
                "F5Setup",
                "no identity at setup screen — skipping setup, advancing wizard",
            )
            passphrase.fill(' ')
            return
        }

        when (val rkResult = rootKeyManager.getOrCreate(identity)) {
            is Outcome.Success -> {
                val rootKey: RootKey = rkResult.value
                val flow = RecoveryFlow(
                    rootKeyManager = rootKeyManager,
                    backup = recoveryKeyBackup,
                    kdf = argon2idKdf,
                    aead = aead,
                    random = random,
                    prompter = OneShotPassphrasePrompter(passphrase),
                    attemptCounter = attemptCounter,
                )
                when (val r = flow.performSetup(identity, rootKey)) {
                    is Outcome.Success -> android.util.Log.i(
                        "F5Setup",
                        "recovery blob uploaded to Worker for stableId=${identity.stableId}",
                    )
                    is Outcome.Failure -> android.util.Log.w(
                        "F5Setup",
                        "recovery setup failed: ${r.error} — user can retry from Settings",
                    )
                }
            }
            is Outcome.Failure -> {
                android.util.Log.w("F5Setup", "rootKey getOrCreate failed: ${rkResult.error}")
                passphrase.fill(' ')
            }
        }
    }

    /** One-shot prompter — returns the wizard-provided CharArray to RecoveryFlow. */
    private class OneShotPassphrasePrompter(
        private val passphrase: CharArray,
    ) : PassphrasePrompter {
        override suspend fun requestSetupPassphrase(): Outcome<CharArray, RecoveryError> {
            if (passphrase.isEmpty()) return Outcome.Failure(RecoveryError.Cancelled)
            return Outcome.Success(passphrase)
        }

        override suspend fun requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError> {
            // Not used in wizard Setup flow.
            return Outcome.Failure(RecoveryError.Cancelled)
        }
    }

    /** One-shot prompter for wizard Entry flow — mirror of setup prompter. */
    private class OneShotRecoveryPrompter(
        private val passphrase: CharArray,
    ) : PassphrasePrompter {
        override suspend fun requestSetupPassphrase(): Outcome<CharArray, RecoveryError> {
            return Outcome.Failure(RecoveryError.Cancelled)
        }

        override suspend fun requestRecoveryPassphrase(): Outcome<CharArray, RecoveryError> {
            if (passphrase.isEmpty()) return Outcome.Failure(RecoveryError.Cancelled)
            return Outcome.Success(passphrase)
        }
    }


    /**
     * After auth choice (signed-in or skipped): show ROLE_HOME step (T042).
     * Wizard progress «Шаг 3/M».
     */
    private fun renderRoleHomeStep() {
        val totalSteps = if (Build.VERSION.SDK_INT >= 33) 5 else 4
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
                            currentStep = 3,
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
                            currentStep = 4,
                            totalSteps = 5,
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

        private val FALLBACK_REASON_TOO_MANY =
            com.launcher.app.ui.recovery.RecoveryViewModel.FallbackReason.TOO_MANY_ATTEMPTS
        private val FALLBACK_REASON_MALFORMED =
            com.launcher.app.ui.recovery.RecoveryViewModel.FallbackReason.MALFORMED_VAULT
    }
}
