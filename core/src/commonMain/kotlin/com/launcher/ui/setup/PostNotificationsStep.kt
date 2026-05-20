package com.launcher.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Spec 010 T043 — wizard step for POST_NOTIFICATIONS (Android 13+ only,
 * FR-008, US-4).
 *
 * **Skip-on-API<33 logic**: the host wizard (FirstLaunchActivity) decides
 * whether to render this step based on `Build.VERSION.SDK_INT >= 33`. The
 * Composable itself is unaware of the SDK level — keeps it platform-agnostic.
 *
 * Senior-safe text — rationale «Чтобы внук видел, что у тебя всё в порядке»
 * directly addresses the bonded admin metaphor that drives the project
 * (Article VIII senior-safe).
 *
 * Both buttons ≥ 56 dp tall — same senior-safe override as [RoleHomeStep].
 */
@Composable
fun PostNotificationsStep(
    title: String,
    body: String,
    allowLabel: String,
    skipLabel: String,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    topContent: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        topContent()
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Button(
            onClick = onAllow,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(text = allowLabel, style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(text = skipLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}
