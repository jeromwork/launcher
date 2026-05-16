package com.launcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.launcher.ui.admin.EditorActions
import com.launcher.ui.admin.EditorScreen
import com.launcher.ui.admin.HistoryScreen
import com.launcher.ui.admin.OpenAppTilePicker
import com.launcher.ui.contacts.ContactsManageScreen
import com.launcher.ui.health.PhoneHealthIndicatorScreen
import com.launcher.ui.navigation.RootChild
import com.launcher.ui.navigation.RootComponent
import com.launcher.ui.screens.AddFlowWizardScreen
import com.launcher.ui.screens.AddSlotWizardScreen
import com.launcher.ui.screens.AdminDevicesScreen
import com.launcher.ui.screens.FirstLaunchScreen
import com.launcher.ui.screens.HomeScreen
import com.launcher.ui.screens.PresetUiModel
import com.launcher.ui.screens.SettingsScreen

/**
 * Renders the root [RootComponent]'s child stack. Each [RootChild] picks its
 * Composable.
 *
 * @param presetUiModels already-localized UI models for the FirstLaunch picker.
 *   Caller (Activity / iOS app entry) resolves localized strings; this avoids
 *   pulling Android resource lookup into commonMain.
 * @param homeTopSlot platform-specific banner host (spec 006 FR-026/027).
 *   Android passes `HomeBannerHost` here. Default no-op slot keeps commonMain
 *   testable без Android dependency.
 */
@Composable
fun RootContent(
    component: RootComponent,
    presetUiModels: List<PresetUiModel>,
    modifier: Modifier = Modifier,
    homeTopSlot: @Composable () -> Unit = {},
) {
    Children(stack = component.stack, modifier = modifier.fillMaxSize()) { childCreated ->
        when (val child = childCreated.instance) {
            is RootChild.FirstLaunch -> FirstLaunchScreen(
                presets = presetUiModels,
                onPresetSelected = child.component.onPresetSelected,
            )
            is RootChild.Home -> HomeScreen(component = child.component, topSlot = homeTopSlot)
            is RootChild.Settings -> SettingsScreen(component = child.component)
            is RootChild.AddFlowWizard -> AddFlowWizardScreen(component = child.component)
            is RootChild.AddSlotWizard -> AddSlotWizardScreen(component = child.component)
            is RootChild.AdminDevices -> AdminDevicesScreen(component = child.component)

            // ─── Spec 009 admin-mode-flows screens ─────────────────────
            is RootChild.Editor -> {
                val state by child.component.state.collectAsState()
                EditorScreen(
                    state = state,
                    actions = EditorActions(
                        onSlotTap = { /* preview action — TODO when wired with ActionDispatcher */ },
                        onSlotLongPress = { /* drag — Phase F */ },
                        onSlotEditMenu = { /* edit menu — TODO Phase E follow-up */ },
                        onPublish = { child.component.publish() },
                    ),
                )
            }
            is RootChild.History -> {
                val state by child.component.state.collectAsState()
                HistoryScreen(
                    snapshots = state.snapshots,
                    rollbackAllowed = child.component.rollbackAllowed,
                    onPreview = { /* TODO: preview screen Phase 14 follow-up */ },
                    onRollback = { snap -> child.component.rollback(snap) },
                    formatTimestamp = { millis -> formatTimestampDefault(millis) },
                )
            }
            is RootChild.ContactsManage -> {
                val contacts by child.component.contacts.collectAsState()
                ContactsManageScreen(
                    contacts = contacts,
                    onDelete = { c -> child.component.deleteContact(c) },
                )
            }
            is RootChild.OpenAppPicker -> {
                val apps by child.component.apps.collectAsState()
                OpenAppTilePicker(
                    apps = apps,
                    onSelect = { app -> child.component.onSelected(app) },
                )
            }
            is RootChild.PhoneHealth -> {
                val indicators by child.component.indicators.collectAsState()
                PhoneHealthIndicatorScreen(
                    title = "Здоровье устройства",
                    indicators = indicators,
                )
            }
        }
    }
}

/**
 * Minimal default timestamp formatter — commonMain pure-Kotlin. Adapters
 * can override at the screen level for locale-aware formatting.
 */
private fun formatTimestampDefault(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val seconds = epochMillis / 1000
    return "+${seconds}s"  // placeholder until DateTime port lands; admin sees relative anchor.
}
