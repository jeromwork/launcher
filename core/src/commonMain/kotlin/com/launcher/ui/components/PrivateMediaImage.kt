package com.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Spec 012 — async-load image для `private:<uuid>` iconRef.
 *
 * Caller передаёт [iconRef], [linkId], и [resolveBitmap] suspend lambda
 * (host-side wrapper над `PrivateMediaResolver.resolve()` который возвращает
 * платформенный `ImageBitmap`). Compose-side:
 *  - Initial: показывает [placeholder] (initial-letter avatar или иконка).
 *  - Загружено: показывает image.
 *  - Failed: stays на placeholder (host эмитит partialApplyReasons отдельно).
 *
 * Task: T1242 (Phase 6). FR-002, FR-014.
 *
 * Implementation note: чтобы избежать decode на main thread, [resolveBitmap]
 * MUST suspend (host wraps в Dispatchers.Default + Bitmap.decode).
 */
@Composable
fun PrivateMediaImage(
    iconRef: String?,
    linkId: String,
    resolveBitmap: suspend (iconRef: String, linkId: String) -> ImageBitmap?,
    fallbackInitial: String,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    var bitmap by remember(iconRef, linkId) { mutableStateOf<ImageBitmap?>(null) }
    var attemptFailed by remember(iconRef, linkId) { mutableStateOf(false) }

    LaunchedEffect(iconRef, linkId) {
        if (iconRef == null || !iconRef.startsWith("private:")) {
            attemptFailed = true
            return@LaunchedEffect
        }
        val resolved = try {
            resolveBitmap(iconRef, linkId)
        } catch (_: Throwable) {
            null
        }
        if (resolved != null) {
            bitmap = resolved
        } else {
            attemptFailed = true
        }
    }

    val shapeModifier = if (circle) Modifier.clip(CircleShape) else Modifier

    Box(
        modifier = modifier.size(sizeDp).then(shapeModifier),
        contentAlignment = Alignment.Center,
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = fallbackInitial,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            InitialPlaceholder(initial = fallbackInitial)
        }
    }
}

@Composable
private fun InitialPlaceholder(initial: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val displayInitial = initial.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Text(
            text = displayInitial,
            fontSize = 32.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
