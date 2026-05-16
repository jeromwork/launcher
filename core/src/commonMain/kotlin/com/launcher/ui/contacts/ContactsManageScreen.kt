package com.launcher.ui.contacts

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
import com.launcher.api.config.Contact

/**
 * Privacy-minimum contacts management screen (spec 009 FR-031a). Privacy
 * compliance is **PLAY-STORE-BLOCKER** until TODO-LEGAL-001 closes —
 * this screen ships the minimum required to delete PII третьих лиц
 * (Маша can ask admin "не показывай меня там больше"; FR-031c export
 * is deferred until TODO-LEGAL-001).
 *
 * Shows the list of `/config/current.contacts[]` for the selected
 * Managed device with a delete affordance. Delete is destructive — gated
 * by a confirmation dialog.
 *
 * Senior-safe: row height ≥ 56 dp, body ≥ 18 sp.
 */
@Composable
fun ContactsManageScreen(
    contacts: List<Contact>,
    onDelete: (Contact) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf<Contact?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Управление контактами",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        if (contacts.isEmpty()) {
            Text(
                text = "Контактов пока нет.",
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
                items(contacts, key = { it.id.value }) { contact ->
                    ContactRow(
                        contact = contact,
                        onDelete = { confirmDelete = contact },
                    )
                }
            }
        }
    }

    val pending = confirmDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить контакт у бабушки?") },
            text = {
                Text("Бабушка больше не сможет позвонить ${'$'}{pending.displayName} с экрана быстрого набора.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pending)
                    confirmDelete = null
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = contact.displayName,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = contact.phoneNumber,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onDelete) {
                Text("Удалить", fontSize = 16.sp)
            }
        }
    }
}
