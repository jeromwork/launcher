package com.launcher.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.config.Contact
import com.launcher.api.config.ValidationError
import com.launcher.api.result.Outcome

/**
 * Manual contact entry (spec 009 FR-023a, FR-026). Used:
 *   - as alternative to system contact picker when READ_CONTACTS denied
 *     (FR-023b deep-link sibling);
 *   - as the universal entry point regardless of permission — admin can
 *     always type a number;
 *
 * Validation pipes through [Contact.fromRaw] so the rules are identical
 * to picker + vCard channels (the single-entry-point invariant in
 * data-model.md §1).
 *
 * State persisted across configuration changes via [rememberSaveable]
 * (spec 008 §FR-056 — autosave-during-edit; here lighter — survives
 * rotation only). Full Activity-death restore lives in the host VM.
 */
@Composable
fun ManualContactEntryForm(
    onConfirm: (Contact) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<ValidationError?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Новый контакт",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Имя") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Телефон") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        val err = error
        if (err != null) {
            Text(
                text = err.toUserMessage(),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = {
                when (val r = Contact.fromRaw(name, phone)) {
                    is Outcome.Success -> onConfirm(r.value)
                    is Outcome.Failure -> error = r.error
                }
            },
            enabled = name.isNotBlank() && phone.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить", fontSize = 18.sp)
        }
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Отмена", fontSize = 18.sp)
        }
    }
}

private fun ValidationError.toUserMessage(): String = when (this) {
    ValidationError.NameEmpty -> "Введите имя"
    is ValidationError.NameTooLong -> "Имя длиннее ${'$'}max символов"
    is ValidationError.NameInvalid -> "Имя содержит недопустимые символы"
    ValidationError.PhoneEmpty -> "Введите телефон"
    is ValidationError.PhoneInvalid -> "Неверный формат телефона"
}
