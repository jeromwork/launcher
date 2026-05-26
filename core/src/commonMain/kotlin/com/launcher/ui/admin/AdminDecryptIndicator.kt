package com.launcher.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.api.media.PrivateMediaResolution

/**
 * Spec 012 — admin-side indicator widget surfacing decrypt failures (FR-022).
 *
 * Displayed по результату чтения `/state/current.partialApplyReasons` где
 * `MediaDecryptFailed` присутствует. Categorised hint guides admin к recovery:
 *  - mac_failed / blob_missing → "Попробовать добавить ещё раз" (re-add).
 *  - key_not_found / recipient_not_found → "Повторить соединение устройств"
 *    (re-pair).
 *
 * Task: T1241 (Phase 5). FR-022.
 */
@Composable
fun AdminDecryptIndicator(
    state: AdminDecryptIndicatorState,
    onReAdd: () -> Unit,
    onRePair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Фото не отрисовалось у ${state.managedDisplayName}",
                fontSize = 18.sp,
            )
            when (state.hint) {
                AdminDecryptHint.ReAdd -> {
                    Text(
                        text = "Попробуйте добавить контакт или документ ещё раз — данные могут быть повреждены.",
                        fontSize = 16.sp,
                    )
                    TextButton(onClick = onReAdd) {
                        Text("Попробовать снова", fontSize = 18.sp)
                    }
                }
                AdminDecryptHint.RePair -> {
                    Text(
                        text = "Похоже, ключи устройств разошлись. Повторите соединение устройств.",
                        fontSize = 16.sp,
                    )
                    TextButton(onClick = onRePair) {
                        Text("Повторить соединение устройств", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/**
 * Pure state derived by host (ViewModel) from `/state.partialApplyReasons` +
 * categorised subcategory. Compose just renders.
 */
data class AdminDecryptIndicatorState(
    val managedDisplayName: String,
    val hint: AdminDecryptHint,
)

enum class AdminDecryptHint {
    /** Re-create the affected contact / document with a fresh photo. */
    ReAdd,

    /** Re-pair devices because cryptographic keys diverged. */
    RePair,
}

/**
 * Spec 012 — map [PrivateMediaResolution.FailureReason] → user-facing hint
 * category. Centralised mapping so admin UI и diagnostics стучатся одинаково.
 */
fun PrivateMediaResolution.FailureReason.toAdminHint(): AdminDecryptHint = when (this) {
    PrivateMediaResolution.FailureReason.BlobMissing -> AdminDecryptHint.ReAdd
    PrivateMediaResolution.FailureReason.MacFailed -> AdminDecryptHint.ReAdd
    PrivateMediaResolution.FailureReason.NetworkError -> AdminDecryptHint.ReAdd
    PrivateMediaResolution.FailureReason.InvalidRef -> AdminDecryptHint.ReAdd
    PrivateMediaResolution.FailureReason.RecipientNotFound -> AdminDecryptHint.RePair
    PrivateMediaResolution.FailureReason.KeyNotFound -> AdminDecryptHint.RePair
}
