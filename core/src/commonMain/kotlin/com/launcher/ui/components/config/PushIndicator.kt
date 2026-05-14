package com.launcher.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Push status indicator composable (spec 008 Phase 8 T101, FR-015 + SC-001b).
 *
 * Renders one of: spinner / «Отправлено ✓» / «Применено на телефоне ✓» /
 * «Нет интернета, попробуем позже» / «Не удалось отправить».
 *
 * **Wording principle** (elderly-friendly CHK009 fix): no developer jargon —
 * никаких «push», «сервер», «бабушка». Neutral, plain Russian.
 *
 * **Test tag** [TEST_TAG] for Compose UI snapshot / state-restoration tests
 * (T102 + future Robolectric coverage).
 */
@Composable
fun PushIndicator(
    state: PushIndicatorState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is PushIndicatorState.Idle -> {
                // Render nothing (no badge in Idle state).
            }

            is PushIndicatorState.InProgress -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }

            is PushIndicatorState.InProgressNoNetwork -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Нет интернета, попробуем позже",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is PushIndicatorState.Sent -> {
                Text(
                    text = "Отправлено ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is PushIndicatorState.AppliedOnDevice -> {
                Text(
                    text = "Применено на телефоне ✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is PushIndicatorState.Failed -> {
                Text(
                    text = "Не удалось отправить",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

const val TEST_TAG: String = "spec008.push_indicator"
