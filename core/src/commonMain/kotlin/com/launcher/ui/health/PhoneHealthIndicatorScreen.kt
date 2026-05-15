package com.launcher.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phone-health monitoring screen (spec 009 FR-017..FR-022a, US-2).
 *
 * Lists 4 indicators (battery / connectivity / audio / lastSeen) emitted
 * by [HealthToPhoneIndicatorAdapter]. List is `LazyColumn` keyed by
 * indicator id so swap-in updates animate without recomposing rows that
 * didn't change.
 *
 * FR-020 (no polling) — каждая фаза реализации читает Firestore listener
 * только when screen is open. Listener wiring lives in the VM, not here.
 *
 * Senior-safe: ≥ 56 dp tap targets (handled by [PhoneHealthIndicatorRow]),
 * ≥ 18 sp body text, ≥ 16 dp spacing.
 */
@Composable
fun PhoneHealthIndicatorScreen(
    title: String,
    indicators: List<PhoneHealthIndicator>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(indicators, key = { it.id }) { indicator ->
                PhoneHealthIndicatorRow(
                    indicator = indicator,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
