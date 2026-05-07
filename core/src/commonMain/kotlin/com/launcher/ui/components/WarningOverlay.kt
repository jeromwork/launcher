package com.launcher.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Generic warning dialog used after a failed handoff or any other operational
 * error that should not be silent. Single dismiss action — keeps cognitive load
 * low (Article VIII).
 */
@Composable
fun WarningOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("warning_dialog"),
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("warning_dismiss_button"),
            ) {
                Text("Понятно", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
