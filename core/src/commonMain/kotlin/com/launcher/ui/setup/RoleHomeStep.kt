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
import androidx.compose.foundation.layout.widthIn
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
 * Spec 010 T042 — first wizard step (FR-007, US-2): «Сделать главным».
 *
 * Senior-safe layout per Article VIII (CHK-elderly-006):
 *  - Title ≥ 24sp via `headlineSmall`.
 *  - Body ≥ 18sp via `bodyLarge` with long-form rationale.
 *  - **Both buttons ≥ 56 dp tall** — exceeds the 48dp Material baseline
 *    (project senior-safe override).
 *  - «Сделать главным» (primary) above «Позже» (outlined).
 *
 * All strings supplied by host (FR-039) — Composable accepts them via
 * parameters so it stays platform-agnostic.
 */
@Composable
fun RoleHomeStep(
    title: String,
    body: String,
    makeDefaultLabel: String,
    skipLabel: String,
    onMakeDefault: () -> Unit,
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
            onClick = onMakeDefault,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .widthIn(min = 200.dp),
        ) {
            Text(text = makeDefaultLabel, style = MaterialTheme.typography.titleMedium)
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
