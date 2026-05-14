package com.launcher.ui.components.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import launcher.core.generated.resources.Res
import launcher.core.generated.resources.config_sync_banner_action_discard
import launcher.core.generated.resources.config_sync_banner_action_push_now
import launcher.core.generated.resources.config_sync_banner_pending_title
import org.jetbrains.compose.resources.stringResource

/**
 * Pending-changes banner для Settings screen (spec 008 Phase 8 T104, FR-047).
 *
 * Shown в Settings того устройства, для которого у user есть несинхронизированные
 * локальные изменения.
 *
 * **Wording per elderly-friendly CHK009 fix**: «Есть изменения, которые ещё не
 * отправлены». Никаких «push», «локальные», «сервер».
 *
 * Actions:
 *  - «Отправить сейчас» (primary) — triggers ConfigEditor.pushPending();
 *  - «Отменить изменения» (destructive) — opens [DiscardConfirmDialog] (FR-057).
 */
@Composable
fun PendingBanner(
    onPushNow: () -> Unit,
    onDiscardRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PENDING_BANNER_TEST_TAG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.config_sync_banner_pending_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPushNow,
                    modifier = Modifier.testTag(PENDING_BANNER_PUSH_BUTTON_TEST_TAG),
                ) {
                    Text(stringResource(Res.string.config_sync_banner_action_push_now))
                }
                TextButton(
                    onClick = onDiscardRequested,
                    modifier = Modifier.testTag(PENDING_BANNER_DISCARD_BUTTON_TEST_TAG),
                ) {
                    Text(stringResource(Res.string.config_sync_banner_action_discard))
                }
            }
        }
    }
}

const val PENDING_BANNER_TEST_TAG: String = "spec008.pending_banner"
const val PENDING_BANNER_PUSH_BUTTON_TEST_TAG: String = "spec008.pending_banner.push"
const val PENDING_BANNER_DISCARD_BUTTON_TEST_TAG: String = "spec008.pending_banner.discard"
