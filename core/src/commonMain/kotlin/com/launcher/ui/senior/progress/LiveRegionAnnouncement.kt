package com.launcher.ui.senior.progress

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer

/**
 * Polite live-region announcement for state changes. TalkBack speaks the
 * latest value when it changes. Per FR-008b.
 *
 * Renders visually empty — text shows in the visible [WizardProgressIndicator],
 * this primitive only carries the TalkBack semantics so sighted users do not
 * see a duplicate label.
 *
 * Common cases: "Шаг 2 из 5", "Настройка применена", "Доступ разрешён".
 */
@Composable
fun LiveRegionAnnouncement(text: String, modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}
