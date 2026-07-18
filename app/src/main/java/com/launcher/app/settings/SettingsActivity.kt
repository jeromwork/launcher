package com.launcher.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import com.launcher.api.localization.StringResolver
import com.launcher.app.HomeActivity
import com.launcher.app.firstlaunch.FirstLaunchActivity
import com.launcher.app.ui.pairing.PairingActivity
import com.launcher.app.wizard.WizardHostActivity
import com.launcher.preset.port.LocalizedResources
import com.launcher.ui.senior.theme.SeniorWarmTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * TASK-69 (FR-017, FR-020, FR-021) — the single Settings host. Absorbs the
 * legacy Decompose `SettingsScreen`/`SettingsComponent` (deleted, see nav
 * audit) and keeps the ECS-era locale-divergence banner + pending-checklist +
 * walk-through-all entry (FR-017). Non-profile legacy sections are re-hosted
 * as [com.launcher.preset.settings.AppOperation]s via existing entry points
 * (FR-020) — their internals are not rewritten.
 */
class SettingsActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by inject()
    private val pendingVmDeps: PendingChecklistViewModel by inject()
    private val localeVmDeps: LocaleDivergenceViewModel by inject()
    private val stringResolver: StringResolver by inject()
    private val i18n: LocalizedResources by inject()
    private val presetRepository: PresetRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorWarmTheme.Light {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var pendingState by remember { mutableStateOf(PendingChecklistState(emptyList())) }
                    var localeState by remember {
                        mutableStateOf(LocaleDivergenceState(appLocale = "", systemLocale = ""))
                    }
                    var presetPickerVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        pendingState = pendingVmDeps.load()
                        localeState = localeVmDeps.state()
                    }

                    val uiState by settingsViewModel.uiState.collectAsState()

                    SettingsScreen(
                        uiState = uiState,
                        i18n = i18n,
                        stringResolver = stringResolver,
                        onChange = settingsViewModel::onChange,
                        onBack = { finish() },
                        onPresetChange = { presetPickerVisible = true },
                        onOpenPairing = { startActivity(Intent(this, PairingActivity::class.java)) },
                        onOpenAdminDevices = ::openAdminDevices,
                        onResetConfirmed = ::executeReset,
                        headerContent = {
                            LocaleDivergenceIndicator(state = localeState, stringResolver = stringResolver)
                            PendingChecklistScreen(state = pendingState, stringResolver = stringResolver)
                        },
                        footerContent = {
                            WalkThroughButton(
                                stringResolver = stringResolver,
                                onClick = ::launchWalkThrough,
                                modifier = Modifier.padding(16.dp),
                            )
                        },
                    )

                    if (presetPickerVisible) {
                        PresetPickerDialog(
                            stringResolver = stringResolver,
                            onDismiss = { presetPickerVisible = false },
                            onSelect = { preset ->
                                presetPickerVisible = false
                                selectPreset(preset)
                            },
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

    /**
     * Admin-devices is a Decompose-nav destination inside [HomeActivity]'s
     * `RootComponent` stack (spec 009). Re-hosted here by relaunching Home with an
     * extra it reads on create — same pattern as `VCardReceiveActivity`'s
     * `EXTRA_OPEN_EDITOR_LINK_ID` (FR-020c: existing entry point, not rewritten).
     */
    private fun openAdminDevices() {
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                putExtra(EXTRA_OPEN_ADMIN_DEVICES, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun selectPreset(preset: FlowPreset) {
        lifecycleScope.launch {
            presetRepository.setActivePreset(preset)
            // Theme density depends on the active preset — force a clean restart of
            // Home rather than recreate() across an Activity boundary (FR-020a).
            startActivity(
                Intent(this@SettingsActivity, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            finish()
        }
    }

    /** FR-020d — destructive, confirmed in [SettingsScreen] before this runs; internals re-hosted, not rewritten. */
    private fun executeReset() {
        lifecycleScope.launch {
            presetRepository.clear()
            startActivity(
                Intent(this@SettingsActivity, FirstLaunchActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            finish()
        }
    }

    companion object {
        const val EXTRA_OPEN_ADMIN_DEVICES = "task69_open_admin_devices"
    }
}

private fun FlowPreset.i18nKey(): String = when (this) {
    FlowPreset.WORKSPACE -> "settings_preset_workspace_label"
    FlowPreset.LAUNCHER -> "settings_preset_launcher_label"
    FlowPreset.SIMPLE_LAUNCHER -> "settings_preset_simple_launcher_label"
}

@Composable
private fun PresetPickerDialog(
    stringResolver: StringResolver,
    onDismiss: () -> Unit,
    onSelect: (FlowPreset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResolver.resolve("settings_preset_picker_title"), style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn {
                items(FlowPreset.entries.toList()) { preset ->
                    TextButton(onClick = { onSelect(preset) }, modifier = Modifier.fillMaxSize()) {
                        Text(stringResolver.resolve(preset.i18nKey()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResolver.resolve("settings_preset_picker_close")) }
        },
    )
}
