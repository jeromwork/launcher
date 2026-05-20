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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck

/**
 * Spec 010 T071 — «What needs configuring» screen (FR-020).
 *
 * Two sections in fixed order:
 *  - «Срочно настроить» (Required first).
 *  - «Можно настроить позже» (Recommended).
 *
 * Each row shows a description (per-check) + a «Настроить» button that
 * hands off the check's [IntentSpec] to [onConfigureClick] — the host
 * Activity translates the spec into a real Android Intent and starts it.
 *
 * Items with [CheckStatus.Ok] are hidden — only NotConfigured rows render.
 */
@Composable
fun WhatNeedsConfiguringScreen(
    requiredItems: List<WhatNeedsItem>,
    recommendedItems: List<WhatNeedsItem>,
    requiredSectionTitle: String,
    recommendedSectionTitle: String,
    configureButtonLabel: String,
    onConfigureClick: (IntentSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 16.dp, vertical = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (requiredItems.isNotEmpty()) {
            SectionHeader(text = requiredSectionTitle)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(requiredItems, key = { it.checkId }) { item ->
                    WhatNeedsRow(
                        item = item,
                        configureLabel = configureButtonLabel,
                        onConfigureClick = onConfigureClick,
                    )
                }
            }
            HorizontalDivider()
        }
        if (recommendedItems.isNotEmpty()) {
            SectionHeader(text = recommendedSectionTitle)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recommendedItems, key = { it.checkId }) { item ->
                    WhatNeedsRow(
                        item = item,
                        configureLabel = configureButtonLabel,
                        onConfigureClick = onConfigureClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WhatNeedsRow(
    item: WhatNeedsItem,
    configureLabel: String,
    onConfigureClick: (IntentSpec) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = { onConfigureClick(item.resolveIntent) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(text = configureLabel)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * One row's data — extracted from a [SetupCheck] +
 * `stringResource(R.string.setup_check_${id}_description)`.
 */
data class WhatNeedsItem(
    val checkId: String,
    val criticality: Criticality,
    val description: String,
    val resolveIntent: IntentSpec,
) {
    companion object {
        /**
         * Convenience builder: filter [SetupCheck]s with [CheckStatus.NotConfigured]
         * and produce rows. The [descriptionFor] lambda resolves the per-check
         * description string (host wires this to `stringResource`).
         */
        fun from(
            checks: List<SetupCheck>,
            statuses: Map<String, CheckStatus>,
            descriptionFor: (String) -> String,
        ): List<WhatNeedsItem> =
            checks
                .filter { statuses[it.id] is CheckStatus.NotConfigured }
                .map { check ->
                    WhatNeedsItem(
                        checkId = check.id,
                        criticality = check.criticality,
                        description = descriptionFor(check.id),
                        resolveIntent = check.resolveIntent(),
                    )
                }
    }
}
