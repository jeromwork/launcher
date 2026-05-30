package com.launcher.app.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.app.R

/**
 * Top banner shown while edit mode is active (FR-010 self-edit, FR-014 remote
 * edit). Two variants:
 *  - **Self** ([targetDisplayName] == null): shows only «Готово» CTA.
 *  - **Remote** ([targetDisplayName] != null): shows «Редактируешь телефон X»
 *    + «← Назад» button (FR-014).
 *
 * Plain English fallback ("Editing paired device") used when
 * [targetDisplayName] is blank but remote editing is active — Q7 +
 * localization.md CHK009.
 */
@Composable
fun EditTopBanner(
    isRemoteEdit: Boolean,
    targetDisplayName: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("f014_edit_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRemoteEdit) {
            val titleText = if (!targetDisplayName.isNullOrBlank()) {
                stringResource(R.string.f014_remote_edit_banner_format, targetDisplayName)
            } else {
                stringResource(R.string.f014_remote_edit_banner_fallback)
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val backDescription = stringResource(R.string.f014_a11y_exit_edit_mode)
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("f014_banner_back")
                    .semantics { contentDescription = backDescription },
            ) {
                Text(stringResource(R.string.f014_banner_back))
            }
        } else {
            // Self-edit: only «Готово» CTA aligned right.
            val finishDescription = stringResource(R.string.f014_a11y_finish_editing)
            Row(modifier = Modifier.weight(1f)) {}
            TextButton(
                onClick = onDone,
                modifier = Modifier
                    .testTag("f014_banner_done")
                    .semantics { contentDescription = finishDescription },
            ) {
                Text(stringResource(R.string.f014_banner_done))
            }
        }
    }
}
