package com.launcher.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.config.Contact
import com.launcher.api.config.Slot
import com.launcher.api.config.SlotKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Stateless tile-edit form (spec 009 FR-011) — invoked via TileEditMenu →
 * "Изменить" or via wizard "+ тайл" flow. Lets admin:
 *   1. choose SlotKind (Call / Sms / OpenApp);
 *   2. pick a contact (Call/Sms) from the current draft's contacts list,
 *      or a package name (OpenApp) via the OpenAppPicker entry button;
 *   3. save → produces an updated [Slot]; cancel → discards.
 *
 * Stateless: caller hoists everything. ContactPicker / OpenAppPicker
 * navigation is owned by parent (EditorComponent). When admin returns
 * from OpenAppPicker with a packageName, parent re-invokes this form
 * с pre-filled state via [initialOpenAppPackage].
 *
 * Senior-safe: tap targets ≥ 56 dp (Button defaults).
 */
@Composable
fun TileEditForm(
    initialSlot: Slot,
    availableContacts: List<Contact>,
    initialOpenAppPackage: String? = null,
    onSave: (Slot) -> Unit,
    onCancel: () -> Unit,
    onPickApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by rememberSaveable(initialSlot.id.value) {
        mutableStateOf(initialSlot.kind)
    }
    var contactId by rememberSaveable(initialSlot.id.value) {
        mutableStateOf(initialSlot.args?.contactId().orEmpty())
    }
    var packageName by rememberSaveable(initialSlot.id.value, initialOpenAppPackage) {
        mutableStateOf(initialOpenAppPackage ?: initialSlot.args?.packageName().orEmpty())
    }
    var label by rememberSaveable(initialSlot.id.value) {
        mutableStateOf(initialSlot.args?.label().orEmpty())
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Тайл",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Подпись") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Тип действия", fontSize = 16.sp)
            KindRow(SlotKind.Call, kind, "Позвонить") { kind = it }
            KindRow(SlotKind.Sms, kind, "Написать SMS") { kind = it }
            KindRow(SlotKind.OpenApp, kind, "Открыть приложение") { kind = it }

            when (kind) {
                SlotKind.Call, SlotKind.Sms -> ContactPicker(
                    selectedId = contactId,
                    contacts = availableContacts,
                    onSelect = { contactId = it },
                )
                SlotKind.OpenApp -> OpenAppRow(
                    packageName = packageName,
                    onPickApp = onPickApp,
                )
                SlotKind.Document -> {
                    // Spec 012 — Document slots добавляются через отдельный
                    // flow (AdminAddDocumentScreen), не через этот editor.
                    // Эта ветка достижима только при редактировании уже-
                    // существующего Document slot — content не редактируется
                    // здесь, только metadata (label через label-поле выше).
                }
            }

            val canSave = when (kind) {
                SlotKind.Call, SlotKind.Sms -> contactId.isNotEmpty()
                SlotKind.OpenApp -> packageName.isNotBlank()
                // Spec 012 — Document slot requires documentRef в args; при
                // редактировании из этого editor'а documentRef не меняется,
                // только label. См. AdminAddDocumentScreen для create flow.
                SlotKind.Document -> initialSlot.args?.let {
                    (it["documentRef"] as? JsonPrimitive)?.content?.startsWith("private:") == true
                } ?: false
            } && label.isNotBlank()

            Button(
                onClick = {
                    val newArgs = buildArgs(kind, contactId, packageName, label)
                    // Spec 012 — preserve documentRef for Document slots (not editable here).
                    val finalArgs = if (kind == SlotKind.Document) {
                        val existingDocRef = (initialSlot.args?.get("documentRef") as? JsonPrimitive)?.content
                        if (existingDocRef != null) {
                            buildJsonObject {
                                newArgs.forEach { (k, v) -> put(k, v) }
                                put("documentRef", JsonPrimitive(existingDocRef))
                            }
                        } else newArgs
                    } else newArgs
                    onSave(
                        initialSlot.copy(
                            kind = kind,
                            args = finalArgs,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить", fontSize = 18.sp)
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отмена", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun KindRow(
    target: SlotKind,
    selected: SlotKind,
    label: String,
    onSelect: (SlotKind) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = target == selected,
            onClick = { onSelect(target) },
        )
        Text(label, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ContactPicker(
    selectedId: String,
    contacts: List<Contact>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Контакт", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (contacts.isEmpty()) {
            Text(
                "Сначала добавьте контакт через «Контакты» в admin-меню.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (contact in contacts) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = contact.id.value == selectedId,
                        onClick = { onSelect(contact.id.value) },
                    )
                    Text(
                        text = "${contact.displayName} · ${contact.phoneNumber}",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenAppRow(
    packageName: String,
    onPickApp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Приложение", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (packageName.isBlank()) "Не выбрано" else packageName,
            fontSize = 16.sp,
        )
        OutlinedButton(onClick = onPickApp) {
            Text("Выбрать приложение")
        }
    }
}

// ─── helpers — pure, no Compose ─────────────────────────────────────────

private fun JsonObject.contactId(): String? =
    (this["contactId"] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.packageName(): String? =
    (this["packageName"] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.label(): String? =
    (this["label"] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun buildArgs(
    kind: SlotKind,
    contactId: String,
    packageName: String,
    label: String,
): JsonObject = buildJsonObject {
    if (label.isNotBlank()) put("label", JsonPrimitive(label))
    when (kind) {
        SlotKind.Call, SlotKind.Sms -> if (contactId.isNotEmpty()) put("contactId", JsonPrimitive(contactId))
        SlotKind.OpenApp -> if (packageName.isNotBlank()) put("packageName", JsonPrimitive(packageName))
        SlotKind.Document -> {
            // Spec 012 — Document slot args (documentRef) builds через
            // AdminAddDocumentScreen, не здесь. label обновляется через
            // основной label put() выше — documentRef preserved из initialSlot.
        }
    }
}
