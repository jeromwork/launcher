package com.launcher.app.edit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.app.R

/**
 * Admin-side conflict snackbar shown по FR-016 + Q7 clarification.
 *
 * Surfaces only in [com.launcher.api.edit.EditUiProfile.AdminProfile]
 * context — senior conflict is silently retried per Q7. Wire to
 * [com.launcher.ui.edit.EditModeState.conflictPending] flag.
 *
 * Per FR-017: «Перезаписать» button is **admin-only**; senior would never
 * see this snackbar by design.
 *
 * @param otherActorName name to show in "X just changed it" — typically
 *   the target's display alias (e.g. "Бабушка Маша"); defaults to gender-
 *   neutral "Пользователь" / "User" when alias is unknown.
 */
@Composable
fun ConflictSnackbar(
    otherActorName: String,
    onUpdate: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarDescription = stringResource(
        R.string.f014_conflict_admin_snackbar_format,
        otherActorName,
    )
    Snackbar(
        modifier = modifier
            .padding(16.dp)
            .testTag("f014_conflict_snackbar")
            .semantics { contentDescription = snackbarDescription },
        action = {
            Row {
                TextButton(
                    onClick = onUpdate,
                    modifier = Modifier.testTag("f014_conflict_update"),
                ) {
                    Text(stringResource(R.string.f014_conflict_admin_update))
                }
                TextButton(
                    onClick = onOverwrite,
                    modifier = Modifier.testTag("f014_conflict_overwrite"),
                ) {
                    Text(stringResource(R.string.f014_conflict_admin_overwrite))
                }
            }
        },
        dismissAction = {
            TextButton(onClick = onDismiss) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
        Text(stringResource(R.string.f014_conflict_admin_snackbar_format, otherActorName))
    }
}
