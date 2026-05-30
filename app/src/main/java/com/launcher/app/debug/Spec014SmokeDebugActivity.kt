package com.launcher.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.launcher.api.edit.PickerType
import com.launcher.app.edit.ConflictSnackbar
import com.launcher.app.edit.EditTopBanner
import com.launcher.app.edit.EmptyStateTile
import com.launcher.app.edit.PlaceholderInDevelopmentScreen
import com.launcher.app.edit.PlaceholderKind
import com.launcher.app.edit.TileContextMenu
import com.launcher.app.edit.UnifiedPickerSheet
import com.launcher.app.edit.jiggle
import com.launcher.app.edit.remoteEditFrame

/**
 * Visual smoke screen for spec 014 F-014.0 composables. Renders all of them
 * на single scrollable surface so the developer can:
 *  - Verify Russian / English localisation toggles correctly.
 *  - Verify jiggle animation runs.
 *  - Verify remote frame visible.
 *  - Verify picker tab filtering by triggering buttons.
 *  - Verify placeholder screens for Widget / Action / Custom preset.
 *  - Verify TalkBack context menu appears.
 *  - Verify conflict snackbar shows admin actions.
 *
 * Launch via `adb shell am start -n com.launcher.app/com.launcher.app.debug.Spec014SmokeDebugActivity`
 * (with `.mock` package suffix for mockBackend variant).
 *
 * NOT part of the public app — debug-only Activity (T200 smoke gate).
 */
class Spec014SmokeDebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Spec014SmokeContent()
            }
        }
    }
}

@Composable
private fun Spec014SmokeContent() {
    var placeholderKind by remember { mutableStateOf<PlaceholderKind?>(null) }
    if (placeholderKind != null) {
        PlaceholderInDevelopmentScreen(
            kind = placeholderKind!!,
            onBack = { placeholderKind = null },
        )
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().testTag("f014_smoke_root"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("F-014.0 visual smoke")

            // ── EditTopBanner self ──────────────────────────────────────
            SectionLabel("EditTopBanner — self (Workspace)")
            EditTopBanner(
                isRemoteEdit = false,
                targetDisplayName = null,
                onDone = {},
                onBack = {},
            )

            // ── EditTopBanner remote ────────────────────────────────────
            SectionLabel("EditTopBanner — remote (Маша)")
            EditTopBanner(
                isRemoteEdit = true,
                targetDisplayName = "Маша",
                onDone = {},
                onBack = {},
            )

            // ── EditTopBanner remote fallback ───────────────────────────
            SectionLabel("EditTopBanner — remote fallback (no alias)")
            EditTopBanner(
                isRemoteEdit = true,
                targetDisplayName = null,
                onDone = {},
                onBack = {},
            )

            HorizontalDivider()

            // ── EmptyStateTile ──────────────────────────────────────────
            SectionLabel("EmptyStateTile (FR-020a — direct picker open)")
            var pickerOpenFromEmpty by remember { mutableStateOf(false) }
            Box(modifier = Modifier.size(160.dp)) {
                EmptyStateTile(onTap = { pickerOpenFromEmpty = true })
            }
            Text(
                text = if (pickerOpenFromEmpty) "tapped → picker would open here" else "tap to open picker",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider()

            // ── Jiggle modifier ─────────────────────────────────────────
            SectionLabel("Jiggle modifier (FR-010 / FR-011)")
            JiggleDemoTiles()

            HorizontalDivider()

            // ── Remote frame ────────────────────────────────────────────
            SectionLabel("RemoteEditFrame (FR-014)")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .remoteEditFrame(active = true),
                contentAlignment = Alignment.Center,
            ) {
                Text("Grid surface (remote edit context)")
            }

            HorizontalDivider()

            // ── UnifiedPickerSheet trigger ──────────────────────────────
            SectionLabel("UnifiedPickerSheet — Workspace target (5 tabs)")
            var pickerWorkspaceOpen by remember { mutableStateOf(false) }
            var pickerSimpleOpen by remember { mutableStateOf(false) }
            var pickedKind by remember { mutableStateOf<PickerType?>(null) }

            Button(
                onClick = { pickerWorkspaceOpen = true },
                modifier = Modifier.testTag("f014_open_picker_workspace"),
            ) { Text("Open Workspace picker (5 tabs)") }

            SectionLabel("UnifiedPickerSheet — Simple Launcher target (3 tabs)")
            Button(
                onClick = { pickerSimpleOpen = true },
                modifier = Modifier.testTag("f014_open_picker_simple"),
            ) { Text("Open Simple Launcher picker (3 tabs)") }

            Text("Last picked: ${pickedKind?.name ?: "—"}")

            if (pickerWorkspaceOpen) {
                UnifiedPickerSheet(
                    visibleTabs = PickerType.entries,
                    onPick = { kind ->
                        pickedKind = kind
                        pickerWorkspaceOpen = false
                    },
                    onShowPlaceholder = { kind ->
                        placeholderKind = when (kind) {
                            PickerType.Widget -> PlaceholderKind.Widget
                            PickerType.Action -> PlaceholderKind.Action
                            else -> null
                        }
                        pickerWorkspaceOpen = false
                    },
                    onDismiss = { pickerWorkspaceOpen = false },
                )
            }

            if (pickerSimpleOpen) {
                UnifiedPickerSheet(
                    visibleTabs = listOf(
                        PickerType.Application,
                        PickerType.Contact,
                        PickerType.Document,
                    ),
                    onPick = { kind ->
                        pickedKind = kind
                        pickerSimpleOpen = false
                    },
                    onShowPlaceholder = { /* never triggered — these tabs hidden */ },
                    onDismiss = { pickerSimpleOpen = false },
                )
            }

            HorizontalDivider()

            // ── Placeholder buttons ─────────────────────────────────────
            SectionLabel("PlaceholderInDevelopmentScreen (FR-018a)")
            Button(
                onClick = { placeholderKind = PlaceholderKind.Widget },
                modifier = Modifier.testTag("f014_show_widget_placeholder"),
            ) { Text("Show Widget placeholder") }
            Button(
                onClick = { placeholderKind = PlaceholderKind.Action },
                modifier = Modifier.testTag("f014_show_action_placeholder"),
            ) { Text("Show Action placeholder") }
            Button(
                onClick = { placeholderKind = PlaceholderKind.CustomPreset },
                modifier = Modifier.testTag("f014_show_custom_preset_placeholder"),
            ) { Text("Show Custom preset placeholder") }

            HorizontalDivider()

            // ── TileContextMenu (FR-012a) ───────────────────────────────
            SectionLabel("TileContextMenu (FR-012a TalkBack alt)")
            var menuOpen by remember { mutableStateOf(false) }
            var lastAction by remember { mutableStateOf("—") }
            Box {
                Button(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("f014_open_tile_context_menu"),
                ) { Text("Open context menu") }
                TileContextMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onMoveUp = { lastAction = "MoveUp" },
                    onMoveDown = { lastAction = "MoveDown" },
                    onMoveLeft = { lastAction = "MoveLeft" },
                    onMoveRight = { lastAction = "MoveRight" },
                    onDelete = { lastAction = "Delete" },
                )
            }
            Text("Last action: $lastAction")

            HorizontalDivider()

            // ── ConflictSnackbar (FR-016 admin) ─────────────────────────
            SectionLabel("ConflictSnackbar (FR-016 admin branch)")
            var conflictResolution by remember { mutableStateOf("—") }
            ConflictSnackbar(
                otherActorName = "Маша",
                onUpdate = { conflictResolution = "Update" },
                onOverwrite = { conflictResolution = "Overwrite" },
                onDismiss = { conflictResolution = "Dismissed" },
            )
            Text("Last conflict resolution: $conflictResolution")

            HorizontalDivider()
            SectionLabel("End of smoke")
        }
    }
}

@Composable
private fun JiggleDemoTiles() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = (1..6).toList()) { index, n ->
            Card(
                modifier = Modifier
                    .size(80.dp)
                    .jiggle(active = true, reducedMotion = index == 5),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (index == 5) "$n (reduced)" else "$n",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}
