package com.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Generic crisis banner для in-launcher alerts (FR-026, FR-027). Senior-safe
 * metrics from FR-058:
 *  - body text ≥ 18sp,
 *  - action button label ≥ 18sp,
 *  - tap area ≥ 56dp height + ≥ 56dp width,
 *  - spacing ≥ 16dp between elements.
 *
 * Material 3 Card + Button. Colour roles: errorContainer / onErrorContainer
 * для visual urgency без hardcoded color values (theming-friendly).
 *
 * Этот Composable знает только данные — text, icon, action — никакого
 * привязки к Settings.Global или AudioManager. Wiring живёт в `:app`
 * HomeBannerHost (Android-specific).
 */
@Composable
fun AlertBannerCard(
    icon: Painter,
    iconContentDescription: String,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), // spacing ≥ 16dp (FR-058)
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = icon,
                contentDescription = iconContentDescription, // TalkBack a11y
                modifier = Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = text,
                    fontSize = 18.sp, // ≥ 18sp body (FR-058)
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .heightIn(min = 56.dp) // tap area ≥ 56dp height (FR-058)
                        .sizeIn(minWidth = 56.dp), // tap area ≥ 56dp width
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 18.sp, // ≥ 18sp action (FR-058)
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
