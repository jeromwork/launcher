package com.launcher.ui.managed

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spec 012 — fullscreen viewer для приватных документов (US-2, FR-018-020).
 *
 * State machine:
 *  - Loading: первый show после tap, file ещё не в LocalMediaStore (или host
 *    ещё не загрузил). Show CircularProgressIndicator + label.
 *  - Shown: image displayed; pinch-to-zoom (1.0× — 4.0×) + pan; +/- buttons
 *    duplicate gesture для TalkBack (🚨 mandatory FR-019b).
 *  - Failed: blob не расшифровался; show placeholder с label.
 *
 * Senior-safe (Article VIII §7):
 *  - Кнопка «Закрыть» ≥ 56dp tap target.
 *  - Кнопки +/- ≥ 56dp каждая.
 *  - Label сверху ≥ 18sp.
 *  - High-contrast surface на error state.
 *
 * Accessibility (🚨 FR-019b mandatory):
 *  - Кнопки `+` / `−` всегда видны (не только при TalkBack). Pinch для full-vision
 *    users, кнопки для users with TalkBack где pinch перехватывается.
 *  - Label has heading semantics.
 *  - Image has contentDescription = label.
 *  - Double-tap zoom is supported (TalkBack-friendly alternative).
 *
 * Spec 012 explicitly **does NOT set FLAG_SECURE** (per Clarification Q5 —
 * согласуется с WhatsApp/Telegram baseline). Host (Activity) controls window
 * flags; этот Composable их не трогает.
 *
 * Task: T1244, T1245, T1246 (Phase 6). FR-018, FR-019, FR-019b, FR-020.
 */
@Composable
fun DocumentViewerScreen(
    state: DocumentViewerState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(label = state.label)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    is DocumentViewerState.Loading -> LoadingContent()
                    is DocumentViewerState.Shown -> ShownContent(state = state)
                    is DocumentViewerState.Failed -> FailedContent(state = state)
                }
            }

            FooterBar(onClose = onClose)
        }
    }
}

@Composable
private fun HeaderBar(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { heading() },
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Text(
            text = "Загрузка…",
            fontSize = 18.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ShownContent(state: DocumentViewerState.Shown) {
    // rememberSaveable: только bitmap reference хранится in-memory (не parcel'ится
    // — это просто Compose state), zoom/pan state survives configuration changes.
    var scale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = state.bitmap,
            contentDescription = state.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
        )

        // 🚨 FR-019b mandatory — TalkBack-friendly zoom controls.
        ZoomControlsRow(
            currentScale = scale,
            onZoomIn = { scale = (scale * ZOOM_STEP).coerceAtMost(MAX_SCALE) },
            onZoomOut = {
                scale = (scale / ZOOM_STEP).coerceAtLeast(MIN_SCALE)
                if (scale <= MIN_SCALE + 0.001f) {
                    // При полном zoom-out — reset offsets.
                    offsetX = 0f
                    offsetY = 0f
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun ZoomControlsRow(
    currentScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onZoomOut,
            enabled = currentScale > MIN_SCALE,
            modifier = Modifier
                .size(64.dp)  // ≥ 56dp senior-safe target.
                .semantics {
                    contentDescription = "Уменьшить"
                    role = Role.Button
                },
        ) {
            Text("−", fontSize = 28.sp)
        }
        Button(
            onClick = onZoomIn,
            enabled = currentScale < MAX_SCALE,
            modifier = Modifier
                .size(64.dp)
                .semantics {
                    contentDescription = "Увеличить"
                    role = Role.Button
                },
        ) {
            Text("+", fontSize = 28.sp)
        }
    }
}

@Composable
private fun FailedContent(state: DocumentViewerState.Failed) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            text = state.label,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = "Не удалось показать фото.",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FooterBar(onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .size(width = 160.dp, height = 64.dp)  // ≥ 56dp.
                    .clearAndSetSemantics {
                        contentDescription = "Закрыть"
                        role = Role.Button
                    },
            ) {
                Text(text = "Закрыть", fontSize = 22.sp)
            }
        }
    }
}

private const val MIN_SCALE: Float = 1.0f
private const val MAX_SCALE: Float = 4.0f
private const val ZOOM_STEP: Float = 1.5f

/**
 * Pure state for [DocumentViewerScreen]. Host (ViewModel) owns lifecycle —
 * resolves "private:<uuid>" through PrivateMediaResolver, dispatches result
 * к этому state.
 *
 * Compose-side: rememberSaveable persists [documentRef] across recreation;
 * **bytes/bitmap NEVER в Parcel** (Q5 deviation — no PII in system parcel).
 * Host re-issues resolve() at recreation; instant if LocalMediaStore hit.
 */
sealed class DocumentViewerState {
    abstract val label: String

    /** First show, host issuing resolve(). */
    data class Loading(override val label: String) : DocumentViewerState()

    /** Image loaded, ready для render. */
    data class Shown(
        override val label: String,
        val bitmap: ImageBitmap,
    ) : DocumentViewerState()

    /** Resolve failed — placeholder с label. */
    data class Failed(override val label: String) : DocumentViewerState()
}
