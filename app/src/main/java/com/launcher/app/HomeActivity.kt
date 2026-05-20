package com.launcher.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import com.launcher.api.FlowPreset
import com.launcher.api.FlowRepository
import com.launcher.api.PresetRepository
import com.launcher.app.firstlaunch.FirstLaunchActivity
import com.launcher.app.home.HomeBannerHost
import com.launcher.api.action.ActionDispatcher
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.apps.InstalledAppsCatalog
import com.launcher.api.config.ConfigEditor
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.identity.IdentityProvider
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.ui.RootContent
import com.launcher.ui.health.HealthToPhoneIndicatorAdapter
import com.launcher.ui.navigation.RootComponent
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.theme.LauncherTheme
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

/**
 * Home entry. Hosts a [RootComponent] (Decompose) which decides what to render —
 * Home, Settings, wizards, etc. — based on its child stack. Activity stays thin
 * and Android-specific.
 */
class HomeActivity : ComponentActivity() {

    private val presetRepository: PresetRepository by inject()
    private val flowRepository: FlowRepository by inject()
    private val actionDispatcher: ActionDispatcher by inject()
    private val providerRegistry: ProviderRegistry by inject()
    // Spec 009 — admin-mode dependencies.
    private val configEditor: ConfigEditor by inject()
    private val historyRepository: ConfigHistoryRepository by inject()
    private val installedAppsCatalog: InstalledAppsCatalog by inject()
    private val remoteSyncBackend: RemoteSyncBackend by inject()
    private val identityProvider: IdentityProvider by inject()
    private val linkRegistry: com.launcher.api.link.LinkRegistry by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The active preset is read synchronously: it controls which density variant
        // LauncherTheme uses, and a HomeActivity launch without a preset means the user
        // never went through FirstLaunch (e.g. external HOME-intent before onboarding).
        val activePreset: FlowPreset? = runBlocking { presetRepository.getActivePreset() }

        // Spec 009: self-device-uid for ConfigSnapshot.recordedFromDeviceId
        // (anti-spoof FR-045a — must equal request.auth.uid). Resolved
        // synchronously from the IdentityProvider's hot Flow; matches the
        // pattern в androidRealBackend BackendInit `selfDeviceId` factory.
        val selfDeviceIdProvider: () -> String = {
            identityProvider.currentIdentity()?.firebaseAuthUid
                ?: "unsigned"  // pre-sign-in: spec 009 admin writes fail Security Rules anyway.
        }
        val healthIndicatorAdapter = HealthToPhoneIndicatorAdapter(
            clock = { System.currentTimeMillis() },
        )

        val rootComponent = RootComponent(
            componentContext = defaultComponentContext(),
            presetRepository = presetRepository,
            flowRepository = flowRepository,
            dispatchAction = { action -> actionDispatcher.dispatch(action) },
            providerRegistry = providerRegistry,
            onPresetChanged = { recreate() },
            onResetData = {
                val intent = android.content.Intent(this, FirstLaunchActivity::class.java)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    )
                startActivity(intent)
                finish()
            },
            initialPresetSlug = activePreset?.slug,
            // Spec 009 admin-mode dependencies.
            configEditor = configEditor,
            historyRepository = historyRepository,
            installedAppsCatalog = installedAppsCatalog,
            remoteSyncBackend = remoteSyncBackend,
            healthIndicatorAdapter = healthIndicatorAdapter,
            selfDeviceIdProvider = selfDeviceIdProvider,
            nowMillis = { System.currentTimeMillis() },
            linkRegistry = linkRegistry,
        )

        val presetUiModels = listOf(
            uiModel(FlowPreset.WORKSPACE, R.string.preset_workspace_title, R.string.preset_workspace_description),
            uiModel(FlowPreset.LAUNCHER, R.string.preset_launcher_title, R.string.preset_launcher_description),
            uiModel(
                FlowPreset.SIMPLE_LAUNCHER,
                R.string.preset_simple_launcher_title,
                R.string.preset_simple_launcher_description,
            ),
        )

        // Spec 009 Phase E — VCardReceiveActivity launches us with this
        // extra to jump straight into the editor after adding a vCard
        // contact to draft.
        intent?.getStringExtra(com.launcher.app.contacts.VCardReceiveActivity.EXTRA_OPEN_EDITOR_LINK_ID)
            ?.let { linkId -> rootComponent.openEditor(linkId) }

        val challengeGateLabels = com.launcher.ui.gate.ChallengeGateLabels(
            cancelLabel = getString(R.string.challenge_gate_cancel),
            sequenceInstructionTemplate = { sequence ->
                getString(R.string.challenge_gate_sequence_instruction, sequence)
            },
        )

        setContent {
            LauncherTheme(preset = activePreset?.slug) {
                RootContent(
                    component = rootComponent,
                    presetUiModels = presetUiModels,
                    homeTopSlot = { HomeBannerHost() },
                    challengeGateLabels = challengeGateLabels,
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
}
