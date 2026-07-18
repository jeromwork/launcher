package com.launcher.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver

/**
 * Senior-safe banner + list of pending setup items (FR-014 /
 * Сценарий 4). The banner renders only when the underlying
 * [PendingChecklistState] has at least one item; otherwise the
 * screen is silent.
 */
@Composable
fun PendingChecklistScreen(
    state: PendingChecklistState,
    stringResolver: StringResolver,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "[!] " + stringResolver.resolve("settings_pending_indicator_label"),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // TASK-69: this banner is now nested inside SettingsScreen's own
                // LazyColumn (as a header item) — a LazyColumn here would hit
                // Compose's "infinity maximum height constraints" crash. The
                // pending-items list is always short (setup checklist, not a
                // data feed), so a plain Column is correct, not a lazy-loading
                // compromise.
                state.items.forEach { item ->
                    PendingItemRow(item = item, stringResolver = stringResolver)
                }
            }
        }
    }
}

@Composable
private fun PendingItemRow(
    item: PendingChecklistState.Item,
    stringResolver: StringResolver,
) {
    val label = stringResolver.resolve(item.labelKey)
    Text(
        text = if (item.isRequired) "• $label" else "○ $label",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
