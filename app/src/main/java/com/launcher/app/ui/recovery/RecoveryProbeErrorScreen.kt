package com.launcher.app.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * task-119 safety brake — shown when [renderRecoveryBranchStep] cannot
 * confirm whether an existing recovery blob exists for the signed-in
 * identity (any outcome other than `Outcome.Success` or `BackupError.NotFound`).
 *
 * Overwriting a returning user's blob is a one-way door: the old root key
 * is unrecoverable and every previously encrypted piece of data becomes
 * garbage. Silent fall-through to Setup on `AuthExpired` or
 * `NetworkUnavailable` (as in the pre-fix code) is therefore not allowed.
 *
 * The user picks one of three explicit options:
 *  - **Повторить проверку** → retry `fetchBlob` (typical fix on Sign-In race
 *    with identity-worker claim propagation, ~1-3s window).
 *  - **Настроить с нуля** → user knows they never set a passphrase before
 *    (or accepts the data loss); wizard advances to Setup.
 *  - **Пропустить** → skip recovery entirely, keep root key device-local;
 *    can be resumed from Settings later.
 */
@Composable
fun RecoveryProbeErrorScreen(
    onRetry: () -> Unit,
    onSetupAnyway: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val title = "Не удалось проверить резервную копию"
        Text(
            text = title,
            modifier = Modifier.semantics { contentDescription = title },
        )

        Text(
            text = "Мы не смогли связаться с сервером, чтобы узнать, " +
                "есть ли у этого аккаунта резервная копия пароля.",
        )

        Text(
            text = "Если у вас уже был пароль на другом устройстве — " +
                "нажмите «Повторить проверку». Настройка с нуля перезапишет " +
                "старую резервную копию, и данные, зашифрованные старым " +
                "паролем, восстановить не получится.",
        )

        Button(onClick = onRetry) {
            Text("Повторить проверку")
        }

        OutlinedButton(onClick = onSetupAnyway) {
            Text("Настроить с нуля (у меня не было пароля)")
        }

        TextButton(onClick = onSkip) {
            Text("Пропустить")
        }
    }
}
