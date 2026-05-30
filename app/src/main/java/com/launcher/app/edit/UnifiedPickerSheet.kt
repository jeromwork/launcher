package com.launcher.app.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.launcher.api.edit.PickerType
import com.launcher.app.R

/**
 * Unified picker bottom sheet showing one tab per [PickerType] (FR-018).
 *
 * Tab visibility filtered by caller per FR-019 — Simple Launcher target
 * receives `visibleTabs = [Application, Contact, Document]` (Widget+Action
 * hidden).
 *
 * Tap on a tab content:
 *  - **Implemented kinds** ([PickerType.Application]/Contact/Document) →
 *    invokes [onPick] with the kind; caller opens specific provider
 *    selection (existing спека 005 wizard, спека 011 contacts picker,
 *    спека 012 documents picker).
 *  - **Placeholder kinds** ([PickerType.Widget]/[PickerType.Action]) →
 *    invokes [onShowPlaceholder] with kind; caller shows
 *    [PlaceholderInDevelopmentScreen].
 *
 * F-014.0 scope: this composable is a **selection shell** only. Real
 * provider list rendering for App / Contact / Document tabs comes from
 * existing wizards / pickers wired via [onPick] callback. T086 covers
 * shell + tab navigation; full provider lists already exist in specs
 * 005 / 011 / 012.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPickerSheet(
    visibleTabs: List<PickerType>,
    onPick: (PickerType) -> Unit,
    onShowPlaceholder: (PickerType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("f014_unified_picker_sheet"),
    ) {
        if (visibleTabs.isEmpty()) {
            Text(
                text = "No tile types available",
                modifier = Modifier.padding(16.dp),
            )
            return@ModalBottomSheet
        }

        TabRow(selectedTabIndex = selectedIndex.coerceAtMost(visibleTabs.lastIndex)) {
            visibleTabs.forEachIndexed { index, kind ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = {
                        selectedIndex = index
                        when (kind) {
                            PickerType.Widget, PickerType.Action -> onShowPlaceholder(kind)
                            else -> onPick(kind)
                        }
                    },
                    text = { Text(pickerTabLabel(kind)) },
                    modifier = Modifier.testTag("f014_picker_tab_${kind.name}"),
                )
            }
        }

        // Tab content area — placeholder height for F-014.0 shell.
        // T090 will replace with provider content (FlowRow / LazyColumn of installed apps etc.).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            val current = visibleTabs.getOrNull(selectedIndex) ?: visibleTabs.first()
            Text(
                text = pickerTabLabel(current),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun pickerTabLabel(kind: PickerType): String = when (kind) {
    PickerType.Application -> stringResource(R.string.f014_picker_tab_apps)
    PickerType.Contact -> stringResource(R.string.f014_picker_tab_contacts)
    PickerType.Document -> stringResource(R.string.f014_picker_tab_documents)
    PickerType.Widget -> stringResource(R.string.f014_picker_tab_widgets)
    PickerType.Action -> stringResource(R.string.f014_picker_tab_actions)
}
