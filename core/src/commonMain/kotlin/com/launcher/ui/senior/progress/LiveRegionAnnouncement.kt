package com.launcher.ui.senior.progress

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * Polite live-region announcement for state changes. TalkBack speaks the
 * latest value when it changes. Per FR-008b.
 *
 * Common cases: "Шаг 2 из 5", "Настройка применена", "Доступ разрешён".
 */
@Composable
fun LiveRegionAnnouncement(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
