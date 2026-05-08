package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.launcher.ui.theme.Spacing
import com.launcher.ui.theme.TapTargets
import kotlin.time.TimeSource

/**
 * Senior-safe tile for a slot on the flow grid. Big icon, big label, big tap target.
 *
 * Spec 005 §7.6 / US-508: tap is **debounced** at 500 ms. Two fast taps in
 * a row (the "Парkinsonian double tap" pattern common in the elderly persona)
 * fire only one dispatch — the second is silently dropped. This is a UX
 * guard, not idempotency in the dispatcher, so it lives here at the
 * widget. The debounce is local per-instance (each [TileCard] keeps its
 * own last-tap timestamp).
 */
@Composable
fun TileCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val debouncedOnClick = rememberDebouncedClick(intervalMs = 500L, onClick = onClick)
    Card(
        onClick = debouncedOnClick,
        modifier = modifier
            .heightIn(min = TapTargets.tile)
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.padding(top = Spacing.sm))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Returns a click handler that ignores re-entrant calls within [intervalMs].
 * Independent per-composition: each [TileCard] gets its own timestamp slot.
 *
 * Uses [TimeSource.Monotonic] so that wall-clock changes (NTP sync, daylight
 * saving) cannot make the handler think it's "already past" the interval.
 */
@Composable
private fun rememberDebouncedClick(
    intervalMs: Long,
    onClick: () -> Unit,
): () -> Unit {
    val mark = remember { LongArray(1) }  // mutable cell; -1 means "no prior tap"
    if (mark[0] == 0L) mark[0] = -1L
    val source = remember { TimeSource.Monotonic }
    val origin = remember { source.markNow() }
    return remember(onClick, intervalMs) {
        {
            val nowMs = origin.elapsedNow().inWholeMilliseconds
            val last = mark[0]
            if (last < 0L || nowMs - last >= intervalMs) {
                mark[0] = nowMs
                onClick()
            }
        }
    }
}
