package com.launcher.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcher.ui.theme.SetupBadgeRed
import com.launcher.ui.theme.SetupBadgeYellow

/**
 * Spec 010 T067 — Settings entrypoint badge surfacing the «[!] N критично» +
 * «[?] M рекомендуется» counts от [SetupCheckEngine] (FR-019).
 *
 * Visual encoding combines THREE signals so we are not colour-only
 * (CHK-accessibility-005, WCAG 1.4.1):
 *  - **Shape**: Required → triangle, Recommended → circle.
 *  - **Colour**: Required → SetupBadgeRed, Recommended → SetupBadgeYellow
 *    (#D97706 per C-7 — not Material yellow which fails contrast).
 *  - **Text label**: «критично» / «рекомендуется».
 *
 * Hidden completely when count == 0 (no zero-state clutter в Settings).
 *
 * TalkBack `contentDescription` is fed from plural-aware strings — host
 * supplies via [requiredA11yLabel] / [recommendedA11yLabel] (already
 * `getQuantityString`-resolved on the Android side).
 */
@Composable
fun SetupChecksBadge(
    requiredCount: Int,
    recommendedCount: Int,
    requiredLabel: String,
    recommendedLabel: String,
    requiredA11yLabel: String,
    recommendedA11yLabel: String,
    modifier: Modifier = Modifier,
) {
    if (requiredCount <= 0 && recommendedCount <= 0) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (requiredCount > 0) {
            BadgeChip(
                count = requiredCount,
                label = requiredLabel,
                a11yLabel = requiredA11yLabel,
                color = SetupBadgeRed,
                shape = TriangleShape,
            )
        }
        if (recommendedCount > 0) {
            BadgeChip(
                count = recommendedCount,
                label = recommendedLabel,
                a11yLabel = recommendedA11yLabel,
                color = SetupBadgeYellow,
                shape = CircleShape,
            )
        }
    }
}

private val TriangleShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun BadgeChip(
    count: Int,
    label: String,
    a11yLabel: String,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Row(
        modifier = Modifier
            .semantics { contentDescription = a11yLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(shape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onError,
                fontSize = 12.sp,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
    Spacer(modifier = Modifier.width(0.dp))
    Spacer(modifier = Modifier.height(0.dp))
    @Suppress("UNUSED_EXPRESSION") shape // already used.
}
