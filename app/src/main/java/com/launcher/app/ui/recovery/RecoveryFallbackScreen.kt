package com.launcher.app.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.launcher.app.ui.recovery.RecoveryViewModel.FallbackReason

/**
 * Fallback screen после неудачи recovery (T072, US2 acceptance 4).
 *
 * Объясняет последствия отказа от recovery:
 *  • Cloud config недоступен — придётся сетап с нуля.
 *  • Pair-keys (S-2 future) пропадают — придётся заново pairing с admin'ом.
 *
 * Reason различия:
 *  • [FallbackReason.TOO_MANY_ATTEMPTS] — 3+ wrong passphrases.
 *  • [FallbackReason.MALFORMED_VAULT] — vault corrupted (cloud tampering, H-2 trigger).
 *  • [FallbackReason.NO_VAULT] — этот UID ещё не setup'нул recovery vault.
 */
@Composable
fun RecoveryFallbackScreen(
    reason: FallbackReason,
    onSetupAsNewDevice: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val title = when (reason) {
            FallbackReason.TOO_MANY_ATTEMPTS -> "Слишком много неверных попыток"
            FallbackReason.MALFORMED_VAULT -> "Не удалось прочитать данные восстановления"
            FallbackReason.NO_VAULT -> "Данные восстановления не найдены"
        }
        Text(
            text = title,
            modifier = Modifier.semantics { contentDescription = title }
        )

        val explanation = when (reason) {
            FallbackReason.TOO_MANY_ATTEMPTS ->
                "Попробуйте позже (счётчик сбросится через час) или настройте телефон как новое устройство."
            FallbackReason.MALFORMED_VAULT ->
                "Данные в облаке повреждены или были изменены. Обратитесь в поддержку или настройте телефон как новое устройство."
            FallbackReason.NO_VAULT ->
                "На этом аккаунте ещё не был настроен пароль восстановления. Настройте телефон как новое устройство."
        }
        Text(text = explanation)

        Text(
            text = "Если настроить как новое: облачная синхронизация конфигурации " +
                "будет начата с нуля. Локальные данные на этом телефоне (если есть) — сохранятся."
        )

        Button(onClick = onSetupAsNewDevice) {
            Text("Настроить как новое устройство")
        }

        if (reason == FallbackReason.TOO_MANY_ATTEMPTS) {
            TextButton(onClick = onRetry) {
                Text("Попробовать ещё раз позже")
            }
        }
    }
}
