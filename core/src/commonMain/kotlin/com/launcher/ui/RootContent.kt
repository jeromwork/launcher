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
import com.launcher.ui.admin.TileEditForm
import com.launcher.ui.contacts.ContactsManageScreen
import com.launcher.ui.gate.ChallengeGateScreen
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

            // Spec 010 T100 — 7-tap admin gate screen (FR-022). Strings are
            // hard-coded fallbacks here because RootContent doesn't take a
            // string-table arg — host wires localized labels via app/.. by
            // overriding this branch in a future refactor; для elderly-target
            // ru-RU launcher the Russian copy below is the visible default.
            is RootChild.ChallengeGate -> ChallengeGateScreen(
                cancelLabel = "Отмена",
                sequenceInstructionTemplate = { sequence -> "Нажми кнопки $sequence по порядку." },
                onSuccess = child.component.onSuccess,
                onCancel = child.component.onCancel,
            )

            // ─── Spec 009 admin-mode-flows screens ─────────────────────
            is RootChild.Editor -> {
                val state by child.component.state.collectAsState()
                EditorScreen(
                    state = state,
                    actions = EditorActions(
                        onSlotTap = { slotId -> child.component.onPreviewTile(slotId) },
                        onSlotLongPress = { /* drag — TileCard wires tileDragSource directly */ },
                        onSlotEditMenu = { slotId ->
                            val flowId = state.draft.flows
                                .firstOrNull { f -> f.slots.any { it.id.value == slotId } }
                                ?.id?.value
                            if (flowId != null) child.component.onEditTile(flowId, slotId)
                        },
                        onPublish = { child.component.publish() },
                        onReorder = { fromFlowId, slotId, toFlowId, toIndex ->
                            child.component.reorderTile(fromFlowId, slotId, toFlowId, toIndex)
                        },
                        onToggleMode = { child.component.toggleMode() },
                        onHistoryClick = { child.component.onHistoryClick() },
                    ),
                )
            }
            is RootChild.History -> {
                val state by child.component.state.collectAsState()
                HistoryScreen(
                    snapshots = state.snapshots,
                    rollbackAllowed = child.component.rollbackAllowed,
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
            is RootChild.TileEdit -> {
                val state by child.component.state.collectAsState()
                if (state.ready) {
                    TileEditForm(
                        initialSlot = state.effectiveSlot,
                        availableContacts = state.contacts,
                        initialOpenAppPackage = state.pendingOpenAppPackage,
                        onSave = { child.component.save(it) },
                        onCancel = { child.component.onCancel() },
                        onPickApp = { child.component.onPickApp() },
                    )
                }
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
