package com.launcher.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.link.Link
import com.launcher.app.R

/**
 * Settings section showing paired-state + the "Отвязать" double-confirm
 * flow (T088, FR-031–FR-033).
 *
 * Two-stage confirm guards against accidental taps:
 *   1. First tap → opens AlertDialog with explicit "Отвязать навсегда"
 *      button and OutlinedButton "Не отвязывать" cancel.
 *   2. Confirm → invokes [onUnbind].
 *
 * Senior-safe per Article VIII §7: tap targets ≥ 56dp, body text ≥ 18sp.
 */
@Composable
fun PairedStatusSection(
    link: Link,
    onUnbind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.pairing_paired_status_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.pairing_paired_status_admin,
                link.adminId.firebaseAuthUid.take(12),
            ),
            fontSize = 18.sp,
        )
        Text(
            text = stringResource(R.string.pairing_paired_status_link_id, link.linkId.take(12)),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showConfirm = true },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .height(56.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_paired_unbind_button),
                fontSize = 18.sp,
            )
        }
    }

    if (showConfirm) {
        UnbindConfirmDialog(
            onConfirm = {
                showConfirm = false
                onUnbind()
            },
            onCancel = { showConfirm = false },
        )
    }
}

@Composable
private fun UnbindConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(R.string.pairing_unbind_dialog_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.pairing_unbind_dialog_body),
                fontSize = 18.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_unbind_dialog_confirm),
                    fontSize = 18.sp,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_unbind_dialog_cancel),
                    fontSize = 18.sp,
                )
            }
        },
    )
}

