package com.launcher.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ElementId
import com.launcher.api.config.ModifiedContact
import com.launcher.api.config.ModifiedFlow

/**
 * Unified Merge UI (spec 008 Phase 9 T111, FR-050).
 *
 * **One implementation для всех editor типов** (admin-phone / admin-tablet /
 * Managed-phone) — no senior-safe variant per FR-050 documented Article VIII §7
 * exception (7-tap+password barrier as cognitive filter).
 *
 * Renders per-element diff:
 *  - PresetId scalar change → 2 radio options (KeepLocal/KeepServer);
 *  - Modified flow → 2 radio options;
 *  - Modified contact → 2 radio options;
 *  - Added (server-only) и Removed (local-only) — informational sections
 *    without choice (default = server's change wins per FR-053 semantics).
 *
 * Buttons:
 *  - «Сохранить» — disabled until all overlapping choices made;
 *  - «Отмена» — back action; per FR-055 preserves pending без changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    component: MergeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(MERGE_SCREEN_TEST_TAG),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Решите конфликт",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Top hint
                item {
                    Text(
                        text = if (state.isAutoMergeable)
                            "Изменения не пересекаются. Применить оба?"
                        else
                            "Кто-то изменил это раньше. Выберите, что оставить.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                // Preset change.
                state.diff.presetIdChanged?.let { change ->
                    item {
                        PresetChoiceCard(
                            localPreset = change.local,
                            serverPreset = change.server,
                            selected = state.choices.presetChoice,
                            onChoice = component::pickPresetChoice,
                        )
                    }
                }

                // Modified flows.
                items(state.diff.modifiedFlows) { modFlow ->
                    ModifiedFlowCard(
                        modified = modFlow,
                        selected = state.choices.flowChoices[modFlow.id],
                        onChoice = { component.pickFlowChoice(modFlow.id, it) },
                    )
                }

                // Modified contacts.
                items(state.diff.modifiedContacts) { modContact ->
                    ModifiedContactCard(
                        modified = modContact,
                        selected = state.choices.contactChoices[modContact.id],
                        onChoice = { component.pickContactChoice(modContact.id, it) },
                    )
                }

                // Added / removed informational sections.
                if (state.diff.addedFlows.isNotEmpty() || state.diff.addedContacts.isNotEmpty()) {
                    item {
                        InfoCard(
                            title = "Добавлено",
                            count = state.diff.addedFlows.size + state.diff.addedContacts.size,
                        )
                    }
                }
                if (state.diff.removedFlowIds.isNotEmpty() || state.diff.removedContactIds.isNotEmpty()) {
                    item {
                        InfoCard(
                            title = "Удалено",
                            count = state.diff.removedFlowIds.size + state.diff.removedContactIds.size,
                        )
                    }
                }

                // Action buttons.
                item {
                    ActionButtons(
                        canSave = state.canSave,
                        onSave = component::onSave,
                        onCancel = component::onCancel,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChoiceCard(
    localPreset: String,
    serverPreset: String,
    selected: MergeChoice?,
    onChoice: (MergeChoice) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MERGE_PRESET_CARD_TEST_TAG),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Preset", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                label = "Оставить моё: $localPreset",
                selected = selected == MergeChoice.KeepLocal,
                onSelect = { onChoice(MergeChoice.KeepLocal) },
            )
            ChoiceRow(
                label = "Оставить серверное: $serverPreset",
                selected = selected == MergeChoice.KeepServer,
                onSelect = { onChoice(MergeChoice.KeepServer) },
            )
        }
    }
}

@Composable
private fun ModifiedFlowCard(
    modified: ModifiedFlow,
    selected: MergeChoice?,
    onChoice: (MergeChoice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Поток", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                label = "Оставить моё: ${modified.local.title}",
                selected = selected == MergeChoice.KeepLocal,
                onSelect = { onChoice(MergeChoice.KeepLocal) },
            )
            ChoiceRow(
                label = "Оставить серверное: ${modified.server.title}",
                selected = selected == MergeChoice.KeepServer,
                onSelect = { onChoice(MergeChoice.KeepServer) },
            )
        }
    }
}

@Composable
private fun ModifiedContactCard(
    modified: ModifiedContact,
    selected: MergeChoice?,
    onChoice: (MergeChoice) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Контакт", style = MaterialTheme.typography.titleMedium)
            ChoiceRow(
                label = "Оставить моё: ${modified.local.displayName} (${modified.local.phoneNumber})",
                selected = selected == MergeChoice.KeepLocal,
                onSelect = { onChoice(MergeChoice.KeepLocal) },
            )
            ChoiceRow(
                label = "Оставить серверное: ${modified.server.displayName} (${modified.server.phoneNumber})",
                selected = selected == MergeChoice.KeepServer,
                onSelect = { onChoice(MergeChoice.KeepServer) },
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, count: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "$title: $count", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionButtons(
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.testTag(MERGE_SAVE_BUTTON_TEST_TAG),
        ) {
            Text("Сохранить")
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(MERGE_CANCEL_BUTTON_TEST_TAG),
        ) {
            Text("Отмена")
        }
    }
}

const val MERGE_SCREEN_TEST_TAG: String = "spec008.merge_screen"
const val MERGE_PRESET_CARD_TEST_TAG: String = "spec008.merge_screen.preset_card"
const val MERGE_SAVE_BUTTON_TEST_TAG: String = "spec008.merge_screen.save"
const val MERGE_CANCEL_BUTTON_TEST_TAG: String = "spec008.merge_screen.cancel"
