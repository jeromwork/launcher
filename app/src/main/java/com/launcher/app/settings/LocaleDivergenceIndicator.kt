package com.launcher.app.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.launcher.api.localization.StringResolver

/**
 * Senior-safe banner shown in Settings when the app's per-app locale
 * diverges from the system locale (FR-017a). Hidden when they match.
 *
 * The label template is resolved through [StringResolver] using key
 * `settings_locale_divergence_label_format` with `%1$s` (app locale)
 * and `%2$s` (system locale) placeholders. We do the substitution
 * inline so the composable stays Compose-only (no String.format / KMP).
 */
@Composable
fun LocaleDivergenceIndicator(
    state: LocaleDivergenceState,
    stringResolver: StringResolver,
    modifier: Modifier = Modifier,
) {
    if (!state.diverges) return
    val template = stringResolver.resolve("settings_locale_divergence_label_format")
    val text = template
        .replace("%1\$s", state.appLocale)
        .replace("%2\$s", state.systemLocale)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(12.dp),
        )
    }
}
