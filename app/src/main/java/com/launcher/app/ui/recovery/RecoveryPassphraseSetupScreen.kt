package com.launcher.app.ui.recovery

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.EditText

/**
 * Setup screen — user придумывает new passphrase (T070, FR-011, FR-013a, FR-014).
 *
 * **Android Autofill**: используется `AutofillHints.NEW_PASSWORD` через
 * нативный EditText (Compose `TextField` не поддерживает autofillHints в Material3
 * стабильно на minSDK 24+; нативный EditText через AndroidView — надёжно).
 *
 * **Clipboard auto-clear** (FR-013a): "Скопировать в буфер обмена" button копирует
 * с EXTRA_IS_SENSITIVE=true (Android 13+) и стартует 60s auto-clear timer. На
 * navigation away — explicit `clearPrimaryClip()`.
 *
 * **Min 8 chars** (FR-014): кнопка "Готово" disabled до 8 chars.
 *
 * **No plaintext display** (FR-013): PasswordVisualTransformation на confirm
 * view; native EditText использует `TYPE_TEXT_VARIATION_PASSWORD`.
 */
@Composable
fun RecoveryPassphraseSetupScreen(
    onSubmit: (CharArray) -> Unit,
    onCancel: () -> Unit
) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf("") }
    var passphraseHolder by remember { mutableStateOf(CharArray(0)) }

    // Clipboard auto-clear on navigation away.
    DisposableEffect(Unit) {
        onDispose {
            clearSensitiveClipboard(ctx)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Придумайте пароль для восстановления",
            modifier = Modifier.semantics { contentDescription = "Заголовок: Придумайте пароль для восстановления" }
        )
        Text(
            text = "Этот пароль понадобится, если вы поменяете телефон. " +
                "Запомните его или сохраните в надёжном месте (например, менеджер паролей)."
        )

        // Native EditText с Autofill newPassword hint.
        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    setAutofillHints("newPassword")
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    hint = "Новый пароль (минимум 8 символов)"
                    contentDescription = "Поле для нового пароля"
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

        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Повторите пароль") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.semantics { contentDescription = "Подтверждение пароля" }
        )

        TextButton(
            onClick = {
                val pw = String(passphraseHolder)
                copyToClipboardSensitive(ctx, pw)
            }
        ) {
            Text("Скопировать в буфер обмена (автоочистка через 60 сек)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isValid = passphraseHolder.size >= 8 && String(passphraseHolder) == confirm
        Button(
            onClick = {
                onSubmit(passphraseHolder.copyOf())
                // Очистим local copy.
                passphraseHolder.fill(' ')
            },
            enabled = isValid
        ) {
            Text("Готово")
        }

        TextButton(onClick = onCancel) {
            Text("Отмена")
        }
    }
}

private fun copyToClipboardSensitive(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = android.content.ClipData.newPlainText("recovery-passphrase", text)
    // Android 13+: пометим как sensitive, чтобы system UI не показывал preview.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cm.setPrimaryClip(clip)
    // Auto-clear через 60s — простой Handler post.
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        clearSensitiveClipboard(ctx)
    }, 60_000L)
}

private fun clearSensitiveClipboard(ctx: Context) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val current = cm.primaryClipDescription
    if (current?.label == "recovery-passphrase") {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
    }
}
