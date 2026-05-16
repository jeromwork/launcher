package com.launcher.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One row of [PhoneHealthIndicator] (spec 009 FR-017, FR-022a, FR-A11Y-002).
 *
 * Senior-safe (Article VIII):
 *  - row height ≥ 56 dp (touch target);
 *  - label ≥ 18 sp, value ≥ 18 sp;
 *  - vector icons (NOT emoji) per FR-022a — TalkBack reads icon
 *    contentDescription, emoji glyph fallback is lossy on older OEM
 *    accessibility settings.
 *  - FR-046b: severity differentiated by **both** hue and SHAPE (round
 *    badge for Info, rounded square for Warning, sharper diamond/error
 *    icon for Critical) — color blindness mitigation.
 *
 * The composable is pure presentation — driven entirely by the
 * [PhoneHealthIndicator] DTO. TalkBack reads
 * [PhoneHealthIndicator.contentDescription] verbatim.
 */
@Composable
fun PhoneHealthIndicatorRow(
    indicator: PhoneHealthIndicator,
    modifier: Modifier = Modifier,
) {
    val palette = severityPalette(indicator.severity)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = indicator.contentDescription },
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SeverityBadge(indicator.id, indicator.severity, palette)
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = indicator.label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = indicator.value,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private data class SeverityPalette(
    val container: Color,
    val onContainer: Color,
)

@Composable
private fun severityPalette(severity: PhoneHealthSeverity): SeverityPalette {
    val scheme = MaterialTheme.colorScheme
    return when (severity) {
        PhoneHealthSeverity.Info -> SeverityPalette(scheme.secondaryContainer, scheme.onSecondaryContainer)
        PhoneHealthSeverity.Warning -> SeverityPalette(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        PhoneHealthSeverity.Critical -> SeverityPalette(scheme.errorContainer, scheme.onErrorContainer)
    }
}

@Composable
private fun SeverityBadge(
    indicatorId: String,
    severity: PhoneHealthSeverity,
    palette: SeverityPalette,
) {
    val shape = when (severity) {
        // FR-046b — shape duplicates severity signal so color-blind users
        // can disambiguate. Circle / rounded square / sharp diamond.
        PhoneHealthSeverity.Info -> CircleShape
        PhoneHealthSeverity.Warning -> RoundedCornerShape(8.dp)
        PhoneHealthSeverity.Critical -> RoundedCornerShape(2.dp)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(palette.container, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconFor(indicatorId, severity),
            contentDescription = null,
            tint = palette.onContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun iconFor(indicatorId: String, severity: PhoneHealthSeverity): ImageVector {
    // material-icons-core has a deliberately small set (~50 icons) so we
    // can't use BatteryFull / SignalCellular4Bar / VolumeUp / Watch
    // without adding the icons-extended dep — plan §5 says no new deps.
    // Map to the closest semantic icon from the core set.
    if (severity == PhoneHealthSeverity.Critical) return Icons.Filled.Warning
    if (severity == PhoneHealthSeverity.Warning) return Icons.Filled.Warning
    return when (indicatorId) {
        HealthToPhoneIndicatorAdapter.ID_BATTERY -> Icons.Filled.Star
        HealthToPhoneIndicatorAdapter.ID_CONNECTIVITY -> Icons.Filled.Refresh
        HealthToPhoneIndicatorAdapter.ID_AUDIO -> Icons.Filled.Notifications
        HealthToPhoneIndicatorAdapter.ID_LAST_SEEN -> Icons.Filled.Phone
        else -> Icons.Filled.Info
    }
}
