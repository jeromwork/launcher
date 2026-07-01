package com.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.profile.SettingEntry

/**
 * Boot-time critical-missing banner on HomeActivity (FR-030, R4, US-7).
 *
 * Dismiss state survives configuration change (rememberSaveable) but NOT
 * process death — banner reappears on next cold boot or state change.
 *
 * Tap target ≥ 56dp; M3 contrast satisfies WCAG 4.5:1.
 */
@Composable
fun HomeBanner(
    criticalMissing: List<SettingEntry>,
    onCtaClick: () -> Unit,
    title: String = "Не настроено важное",
    cta: String = "Настроить",
    dismissLabel: String = "Позже",
    modifier: Modifier = Modifier,
) {
    if (criticalMissing.isEmpty()) return
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = criticalMissing.joinToString(separator = " • ") { it.config.title },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { dismissed = true },
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                ) { Text(dismissLabel) }
                TextButton(
                    onClick = onCtaClick,
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                ) { Text(cta) }
            }
        }
    }
}
