package com.launcher.ui.components.config

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import launcher.core.generated.resources.Res
import launcher.core.generated.resources.config_sync_dialog_action_delete
import launcher.core.generated.resources.config_sync_dialog_action_keep
import launcher.core.generated.resources.config_sync_dialog_discard_body
import launcher.core.generated.resources.config_sync_dialog_discard_title
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation dialog для destructive «Отменить изменения» action
 * (spec 008 Phase 8 T105, FR-057, ux-quality CHK010).
 *
 * **Primary action**: «Оставить» (preserve — safer default).
 * **Destructive secondary**: «Удалить» (errorColor — visual hierarchy).
 *
 * Wording (elderly-friendly CHK009): «Изменения нельзя будет восстановить» —
 * informational, не threatening «нельзя отменить».
 */
@Composable
fun DiscardConfirmDialog(
    onConfirmDiscard: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(DIALOG_TEST_TAG),
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(Res.string.config_sync_dialog_discard_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.config_sync_dialog_discard_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            // "Оставить" = primary safe action (keep pending).
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(DIALOG_KEEP_BUTTON_TEST_TAG),
            ) {
                Text(stringResource(Res.string.config_sync_dialog_action_keep))
            }
        },
        dismissButton = {
            // "Удалить" = destructive secondary (red text).
            TextButton(
                onClick = onConfirmDiscard,
                modifier = Modifier.testTag(DIALOG_DISCARD_BUTTON_TEST_TAG),
            ) {
                Text(
                    text = stringResource(Res.string.config_sync_dialog_action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

const val DIALOG_TEST_TAG: String = "spec008.discard_dialog"
const val DIALOG_KEEP_BUTTON_TEST_TAG: String = "spec008.discard_dialog.keep"
const val DIALOG_DISCARD_BUTTON_TEST_TAG: String = "spec008.discard_dialog.discard"
