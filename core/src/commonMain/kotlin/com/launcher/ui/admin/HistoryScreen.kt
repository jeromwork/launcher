package com.launcher.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.history.ConfigSnapshotWithId

/**
 * History viewer (spec 009 FR-037, FR-039) — full-screen list of past
 * `/config/current` snapshots (newest first), tap → preview, action →
 * rollback (FR-040).
 *
 * FR-042 (editor symmetry): the same screen is reachable from both
 * admin EditorScreen and the Managed-side Settings ("история конфигов"
 * for the senior — read-only, no rollback button).
 *
 * FR-043 schema validation already happened at adapter read; this
 * Composable trusts the input list.
 *
 * Senior-safe: row height ≥ 56 dp, body ≥ 18 sp.
 */
@Composable
fun HistoryScreen(
    snapshots: List<ConfigSnapshotWithId>,
    rollbackAllowed: Boolean,
    onRollback: (ConfigSnapshotWithId) -> Unit,
    formatTimestamp: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    var confirmRollback by remember { mutableStateOf<ConfigSnapshotWithId?>(null) }
    var preview by remember { mutableStateOf<ConfigSnapshotWithId?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "История изменений",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        if (snapshots.isEmpty()) {
            Text(
                text = "История пуста — нажмите «Опубликовать», чтобы появилась первая версия.",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(snapshots, key = { it.autoId }) { snap ->
                    HistoryRow(
                        snap = snap,
                        rollbackAllowed = rollbackAllowed,
                        formatTimestamp = formatTimestamp,
                        onPreview = { preview = snap },
                        onRollback = { confirmRollback = snap },
                    )
                }
            }
        }
    }

    val pending = confirmRollback
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmRollback = null },
            title = { Text("Откатить к этой версии?") },
            text = {
                Text("Текущая раскладка будет заменена версией от ${'$'}{formatTimestamp(pending.snapshot.recordedAt)}. Перед откатом будет сохранён ещё один снимок текущей версии.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRollback(pending)
                    confirmRollback = null
                }) {
                    Text("Откатить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollback = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    val previewSnap = preview
    if (previewSnap != null) {
        SnapshotPreviewDialog(
            snapshot = previewSnap,
            formatTimestamp = formatTimestamp,
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun SnapshotPreviewDialog(
    snapshot: ConfigSnapshotWithId,
    formatTimestamp: (Long) -> String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Снимок от ${'$'}{formatTimestamp(snapshot.snapshot.recordedAt)}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Пресет: ${'$'}{snapshot.snapshot.config.presetId}",
                    fontSize = 16.sp,
                )
                Text(
                    text = "Экранов: ${'$'}{snapshot.snapshot.config.flows.size}",
                    fontSize = 16.sp,
                )
                Text(
                    text = "Тайлов: ${'$'}{snapshot.snapshot.config.flows.sumOf { it.slots.size }}",
                    fontSize = 16.sp,
                )
                Text(
                    text = "Контактов: ${'$'}{snapshot.snapshot.config.contacts.size}",
                    fontSize = 16.sp,
                )
                Text(
                    text = "ID версии: ${'$'}{snapshot.autoId}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun HistoryRow(
    snap: ConfigSnapshotWithId,
    rollbackAllowed: Boolean,
    formatTimestamp: (Long) -> String,
    onPreview: () -> Unit,
    onRollback: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        onClick = onPreview,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Записано ${'$'}{formatTimestamp(snap.snapshot.recordedAt)}",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val flowCount = snap.snapshot.config.flows.size
                val slotCount = snap.snapshot.config.flows.sumOf { it.slots.size }
                val contactCount = snap.snapshot.config.contacts.size
                Text(
                    text = "${'$'}flowCount экранов · ${'$'}slotCount тайлов · ${'$'}contactCount контактов",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rollbackAllowed) {
                OutlinedButton(onClick = onRollback) {
                    Text("Откатить", fontSize = 16.sp)
                }
            }
        }
    }
}
