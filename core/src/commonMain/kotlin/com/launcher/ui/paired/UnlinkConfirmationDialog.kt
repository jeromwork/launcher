package com.launcher.ui.paired

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Spec 010 T083 — двухступенчатое подтверждение «Прекратить помощь» per
 * FR-031 / Article VIII destructive-action paradigm.
 *
 * Why a full AlertDialog (не in-line confirm row): the action is destructive
 * (link revocation is one-way for the elderly user without admin
 * re-handshake), so we need the senior-safe "modal pause" with a clear
 * title + body + ДА/НЕТ pair.
 *
 * Hostable text: the caller passes already-localized strings
 * (`stringResource(R.string.paired_unlink_confirm_title, displayName)` etc.)
 * to keep this Composable platform-pure (CLAUDE.md rule 1 — no
 * `androidx.compose.ui.res.stringResource` calls in commonMain UI).
 */
@Composable
fun UnlinkConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag("paired_unlink_confirm_dialog"),
        onDismissRequest = onCancel,
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .defaultMinSize(minHeight = 56.dp)
                    .testTag("paired_unlink_confirm_yes"),
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .defaultMinSize(minHeight = 56.dp)
                    .testTag("paired_unlink_confirm_no"),
            ) {
                Text(text = cancelLabel)
            }
        },
    )
}
