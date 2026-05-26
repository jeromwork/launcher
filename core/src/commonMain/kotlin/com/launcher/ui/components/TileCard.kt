package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.vector.ImageVector
import com.launcher.api.config.SlotKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    slotKind: SlotKind? = null,
    editMode: Boolean = false,
    dragged: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onEditMenuClick: (() -> Unit)? = null,
) {
    val debouncedOnClick = rememberDebouncedClick(intervalMs = 500L, onClick = onClick)
    val rootClickModifier = if (editMode) {
        // FR-008 / FR-A11Y-004 — long-press triggers drag-anchor or
        // bottom-sheet alternative; short tap опционально проигрывает то
        // же действие что и view mode (для preview).
        Modifier.combinedClickable(
            onClick = debouncedOnClick,
            onLongClick = onLongPress ?: {},
        )
    } else {
        Modifier  // Card composable below uses its own onClick when not editMode.
    }
    // G5 — visual drag decoration: while dragged, scale up + raise elevation
    // so admin sees that the tile is "lifted off the grid" (FR-008).
    val dragModifier = if (dragged) {
        Modifier.graphicsLayer {
            scaleX = 1.05f
            scaleY = 1.05f
            alpha = 0.85f
            shadowElevation = 12.dp.toPx()
        }
    } else {
        Modifier
    }
    Card(
        onClick = if (!editMode) debouncedOnClick else { -> },
        modifier = modifier
            .heightIn(min = TapTargets.tile)
            .fillMaxWidth()
            .then(dragModifier)
            .then(rootClickModifier)
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                dragged -> MaterialTheme.colorScheme.primaryContainer
                editMode -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (editMode) 2.dp else 1.dp),
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
                    // FR-046 fix: vary icon by SlotKind. Falls back to
                    // Call when kind is null (legacy callers).
                    imageVector = iconForSlotKind(slotKind),
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
            // FR-009 / FR-A11Y-004 — alternative to drag for elderly /
            // limited-motor: "···" button surfaces edit menu without
            // requiring a precise long-press.
            if (editMode && onEditMenuClick != null) {
                IconButton(onClick = onEditMenuClick) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Меню тайла",
                    )
                }
            }
        }
    }
}

/**
 * Spec 009 FR-046 fix — TileCard icon now varies by [SlotKind] instead
 * of being hardcoded to `Icons.Filled.Call`. Uses only icons available
 * в `material-icons-core` (plan §5 no new deps); semantic icon proxies
 * tracked в TODO-UI-001 (project-backlog).
 */
internal fun iconForSlotKind(slotKind: SlotKind?): ImageVector = when (slotKind) {
    SlotKind.Call -> Icons.Filled.Phone
    SlotKind.Sms -> Icons.Filled.Send
    SlotKind.OpenApp -> Icons.Filled.Star
    // Spec 012 — Document slot uses generic image-shape icon. Real thumbnail
    // (decrypted document photo) рендерится отдельно через PrivateMediaResolver;
    // эта icon — placeholder fallback (while decrypting OR при ошибке).
    SlotKind.Document -> Icons.Filled.Star
    null -> Icons.Filled.Call
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
