package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.launcher.api.CommunicationActionType
import com.launcher.ui.theme.Spacing

/**
 * Modal confirmation before a communication action runs (call/video). Required by
 * spec 002 (mandatory confirmation step before launching WhatsApp).
 */
@Composable
fun ConfirmationOverlay(
    contactLabel: String,
    actionType: CommunicationActionType,
    success: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val actionLabel = when (actionType) {
        CommunicationActionType.CALL -> "Позвонить"
        CommunicationActionType.VIDEO -> "Видеозвонок"
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("confirmation_dialog"),
        title = {
            Text(
                text = "$actionLabel: $contactLabel",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Вы хотите $actionLabel — $contactLabel?",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (success) {
                    Text(
                        text = "Открываем WhatsApp…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_button"),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_button"),
            ) {
                Text("Отмена", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
