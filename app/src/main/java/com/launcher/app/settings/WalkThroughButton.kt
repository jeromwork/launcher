package com.launcher.app.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver

/**
 * Senior-safe "Walk through all settings step-by-step" button
 * (FR-014a / Сценарий 5). Tap dispatches into the walk-through
 * variant of the wizard engine.
 */
@Composable
fun WalkThroughButton(
    stringResolver: StringResolver,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // senior-safe tap target ≥ 56dp.
            .padding(8.dp),
    ) {
        Text(
            text = stringResolver.resolve("settings_walk_through_all_label"),
        )
    }
}
