package com.launcher.app.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Per FR-014: when admin edits a paired Managed device's config remotely,
 * the editor grid is surrounded by a 4dp colored frame to make the "not your
 * own device" context immediately visible.
 *
 * Per FR-015: NO frame on self-edit. Caller composes this modifier only
 * when `target.isSelf == false`.
 */
@Composable
fun Modifier.remoteEditFrame(active: Boolean): Modifier {
    if (!active) return this
    return this
        .border(
            border = BorderStroke(4.dp, SolidColor(MaterialTheme.colorScheme.tertiary)),
        )
        .testTag("f014_remote_edit_frame")
}
