package com.launcher.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Permission rationale screen for `READ_CONTACTS` (spec 009 FR-023,
 * FR-023a, FR-023b). Shown:
 *   - first time admin taps "+ контакт" in editor;
 *   - after deny (with "Don't ask again" not selected);
 *   - via a deep-link from settings if denied permanently.
 *
 * Senior-safe (Article VIII):
 *   - body ≥ 18 sp;
 *   - two-button choice ("Разрешить" / "Ввести вручную") + "Открыть
 *     настройки" tertiary fallback;
 *   - tap targets ≥ 56 dp height (Material 3 Button default).
 *
 * FR-023b: when user has "Don't ask again" enabled, the host activity
 * surfaces [onOpenSettings] which deep-links to
 * `ACTION_APPLICATION_DETAILS_SETTINGS` (Android-specific; wired in app
 * layer).
 */
@Composable
fun ContactPermissionRationaleScreen(
    onAllow: () -> Unit,
    onManualEntry: () -> Unit,
    onOpenSettings: () -> Unit,
    permanentlyDenied: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Доступ к контактам",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Чтобы добавить контакт из системного списка, разрешите приложению читать контакты. Также можно ввести номер вручную.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!permanentlyDenied) {
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Разрешить", fontSize = 18.sp)
            }
        } else {
            // FR-023b: "Don't ask again" — system prompt won't reappear,
            // route the user to system settings instead.
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть настройки", fontSize = 18.sp)
            }
        }
        OutlinedButton(
            onClick = onManualEntry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Ввести вручную", fontSize = 18.sp)
        }
    }
}
