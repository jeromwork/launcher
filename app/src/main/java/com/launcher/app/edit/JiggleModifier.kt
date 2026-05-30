package com.launcher.app.edit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * Per FR-010: tiles in edit mode rotate ±2° on a 400ms cycle (mainstream
 * Niagara/Pixel pattern).
 *
 * Per FR-011: if [reducedMotion] is `true`, replaces the rotation with a
 * static 2dp accent-coloured border so users with motion sensitivity still
 * see a visible edit-mode affordance без animation.
 *
 * Apply этот modifier к каждой tile в edit mode.
 */
@Composable
fun Modifier.jiggle(active: Boolean, reducedMotion: Boolean): Modifier {
    if (!active) return this
    if (reducedMotion) {
        return this
            .border(
                border = BorderStroke(2.dp, SolidColor(MaterialTheme.colorScheme.primary)),
            )
            .padding(2.dp)
    }
    val transition = rememberInfiniteTransition(label = "f014-jiggle")
    val angle by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "f014-jiggle-angle",
    )
    return this.rotate(angle)
}
