package com.launcher.app.wizard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.launcher.api.localization.StringResolver
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.WizardManifest
import com.launcher.app.HomeActivity
import com.launcher.ui.senior.theme.SeniorWarmTheme
import com.launcher.ui.wizard.WizardHostScreen
import com.launcher.ui.wizard.steps.StepHost
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

/**
 * Spec 015 entry point — runs the wizard for the bundled `simple-launcher`
 * manifest. On completion routes to [HomeActivity]; on
 * `IncompatibleVersion` from the bundled config routes to
 * [PlayStoreFallbackActivity] (FR-016, Q-6 (b)).
 *
 * Per FR-007, FR-008d.
 */
class WizardActivity : ComponentActivity() {

    private val engine: WizardEngine by inject()
    private val configSource: ConfigSource by inject()
    private val stringResolver: StringResolver by inject()
    private val userPreferencesStore: UserPreferencesStore by inject()
    private val presetRepository: PresetRepository by inject()
    private val uiChoiceHost: StepHost by inject(named("uiChoiceHost"))
    private val systemSettingHost: StepHost by inject(named("systemSettingHost"))
    private val tutorialHintHost: StepHost by inject(named("tutorialHintHost"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // FR-008d / Q-1 (b) — at step 0 inform the user; step-internal
                // Back is wired through the UI host's StepHost.resolve.
                Toast.makeText(
                    this@WizardActivity,
                    stringResolver.resolve("wizard_back_at_first_step_toast"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        })

        setContent {
            SeniorWarmTheme.Light {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    when (val loaded = configSource.load(
                        ConfigKind.WizardManifest,
                        "wizard-manifest.simple-launcher",
                    )) {
                        is ConfigSourceResult.Success -> {
                            val doc = loaded.document as? ConfigDocument.Manifest
                            if (doc == null) {
                                routeToFallback()
                                return@LaunchedEffect
                            }
                            val manifest = WizardManifest(doc.header, doc.body)
                            scope.launch {
                                val walkThrough = intent?.getBooleanExtra(EXTRA_WALK_THROUGH, false) == true
                                if (walkThrough) {
                                    engine.runWalkThrough(manifest)
                                } else {
                                    engine.run(manifest)
                                }
                                // TASK-7 / FR-017 — once the wizard captures a
                                // language choice, persist it as the app-level
                                // locale override so subsequent system-locale
                                // changes don't override it (Article III §7).
                                applyLanguageOverride()
                                routeToHome()
                            }
                        }
                        is ConfigSourceResult.IncompatibleVersion -> routeToFallback()
                        is ConfigSourceResult.ParseError,
                        is ConfigSourceResult.NotFound -> {
                            android.util.Log.e("WizardActivity", "Wizard manifest load failed: $loaded")
                            routeToFallback()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    WizardHostScreen(
                        engine = engine,
                        stringResolver = stringResolver,
                        uiChoiceHost = uiChoiceHost,
                        systemSettingHost = systemSettingHost,
                        tutorialHintHost = tutorialHintHost,
                        onCompleted = { routeToHome() },
                        onCancelled = { finish() },
                    )
                }
            }
        }
    }

    private fun routeToHome() {
        lifecycleScope.launch {
            if (presetRepository.getActivePreset() == null) {
                // wizard без выбора пресета = senior default
                presetRepository.setActivePreset(FlowPreset.SIMPLE_LAUNCHER)
            }
            startActivity(
                Intent(this@WizardActivity, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            finish()
        }
    }

    private suspend fun applyLanguageOverride() {
        val override = userPreferencesStore.current().languageOverride ?: return
        val locales = LocaleListCompat.forLanguageTags(override)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        /**
         * Intent extra (`Boolean`) — if true, [WizardActivity] runs the
         * wizard via [WizardEngine.runWalkThrough] (skips computePending
         * pre-flight) rather than the normal first-run [WizardEngine.run].
         * Set by [com.launcher.app.settings.SettingsActivity] Walk-through
         * button (TASK-7 / FR-014a / Сценарий 5).
         */
        const val EXTRA_WALK_THROUGH: String = "com.launcher.app.wizard.EXTRA_WALK_THROUGH"
    }

    private fun routeToFallback() {
        startActivity(
            Intent(this, PlayStoreFallbackActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        finish()
    }
}
