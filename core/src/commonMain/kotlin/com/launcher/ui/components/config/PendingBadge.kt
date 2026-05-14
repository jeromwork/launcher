package com.launcher.ui.components.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import launcher.core.generated.resources.Res
import launcher.core.generated.resources.config_sync_badge_not_sent
import org.jetbrains.compose.resources.stringResource

/**
 * «Не отправлено» badge для admin device-list (spec 008 Phase 8 T107, FR-046,
 * SC-008).
 *
 * Shown рядом с устройством в списке привязанных, если для этого устройства
 * есть pending-local-changes (autosaved но не pushed).
 *
 * Per elderly-friendly CHK018: NOT colour-only — icon-like rounded pill with
 * text label. (Plus optional accessibility content description if a11y
 * checklist gets triggered in a future spec.)
 *
 * Wording (CHK009 fix): «Не отправлено» — neutral, нет «push» / «pending».
 */
@Composable
fun PendingBadge(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(PENDING_BADGE_TEST_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.config_sync_badge_not_sent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

const val PENDING_BADGE_TEST_TAG: String = "spec008.pending_badge"
