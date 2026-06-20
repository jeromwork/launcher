package com.launcher.app.ui.recovery

import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Recovery passphrase entry screen (T071, FR-012, FR-027, US2 acceptance).
 *
 * Autofill hint = `AUTOFILL_HINT_PASSWORD` (для existing password lookup в
 * Google Password Manager).
 *
 * После 3 неудачных попыток UI должен показать `RecoveryFallbackScreen`
 * (decision лежит на NavController + ViewModel state, не на этом screen'е).
 *
 * @param failedAttempts текущий счётчик failed attempts (0..2). >=3 экран не показывается.
 */
@Composable
fun RecoveryPassphraseEntryScreen(
    failedAttempts: Int,
    onSubmit: (CharArray) -> Unit,
    onCancel: () -> Unit,
    onFallback: () -> Unit
) {
    var passphraseHolder by remember { mutableStateOf(CharArray(0)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Введите пароль восстановления",
            modifier = Modifier.semantics { contentDescription = "Заголовок: введите пароль восстановления" }
        )
        Text(
            text = "Это пароль, который вы придумали при первой настройке. " +
                "Если используете менеджер паролей — он подскажет."
        )

        if (failedAttempts > 0) {
            Text(
                text = "Неверный пароль. Осталось попыток: ${3 - failedAttempts}",
                modifier = Modifier.semantics {
                    contentDescription = "Неверный пароль. Осталось попыток: ${3 - failedAttempts}"
                }
            )
        }

        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setAutofillHints("password")
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    hint = "Пароль восстановления"
                    contentDescription = "Поле для пароля восстановления"
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            passphraseHolder = (s?.toString() ?: "").toCharArray()
                        }
                        override fun afterTextChanged(s: android.text.Editable?) {}
                    })
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                onSubmit(passphraseHolder.copyOf())
                passphraseHolder.fill(' ')
            },
            enabled = passphraseHolder.isNotEmpty()
        ) {
            Text("Восстановить")
        }

        TextButton(onClick = onCancel) {
            Text("Отмена")
        }

        if (failedAttempts >= 2) {
            TextButton(onClick = onFallback) {
                Text("Настроить как новое устройство")
            }
        }
    }
}
