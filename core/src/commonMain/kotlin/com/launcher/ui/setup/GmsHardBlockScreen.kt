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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spec 010 T046 — GMS-missing hard-block screen (FR-042, FR-043, US-9).
 *
 * Shown on first launch when [com.launcher.api.setup.GmsAvailabilityPort.status]
 * returns [com.launcher.api.setup.GmsStatus.MissingFatal]. The user can either:
 *  - tap «Подробнее» link to learn what GMS is (opens browser via host
 *    activity — Composable just exposes the callback), or
 *  - tap «Понятно» — host calls `finishAffinity()`.
 *
 * Senior-safe constraints (CHK-elderly-006 + FR-043):
 *  - URL link text **≥ 24 sp** per FR-043.
 *  - Title ≥ 26 sp (`headlineMedium` baseline plus we bump explicitly).
 *  - «Понятно» button ≥ 56 dp tall.
 */
@Composable
fun GmsHardBlockScreen(
    title: String,
    body: String,
    learnMoreLabel: String,
    okLabel: String,
    onLearnMore: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 48.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onLearnMore) {
            Text(
                text = learnMoreLabel,
                fontSize = 24.sp, // FR-043 senior-safe ≥ 24 sp
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onOk,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(text = okLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}
