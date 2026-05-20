package com.launcher.ui.gate

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.launcher.util.nowEpochMillis

/**
 * Spec 010 T094 + T095 + T100 — Compose [Modifier] that wires a
 * [SevenTapDetector] to a Box, firing haptic escalation per tap (FR-021).
 *
 *  - Attaches to non-interactive area only — children with their own click
 *    handlers (tiles, BottomFlowBar buttons) consume the tap первыми;
 *    pointerInput at the parent fires only когда тап «проходит мимо».
 *  - On 7-th tap fires [onTriggered] (host pushes ChallengeGate).
 *  - Haptic:
 *    - [SevenTapDetector.Stage.Light] → `HapticFeedbackType.TextHandleMove`
 *    - [SevenTapDetector.Stage.Medium] → `HapticFeedbackType.LongPress`
 *    - [SevenTapDetector.Stage.Success] → success pattern (LongPress with
 *      delayed second pulse — single pulse будет неотличим от Medium).
 *  - **No visual countdown** (FR-021): admin orients by haptic only.
 *  - **Haptic-disabled edge** (FR-021 / T103): `LocalHapticFeedback` swallows
 *    the call when system haptic is off; the gesture still triggers because
 *    [SevenTapDetector.onTap] does its own counting independent of haptic
 *    return value.
 */
@Composable
fun Modifier.sevenTapAdminGate(
    onTriggered: () -> Unit,
    nowMillis: () -> Long = ::nowEpochMillis,
): Modifier {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val onTriggeredLatest by rememberUpdatedState(onTriggered)
    val detector = remember {
        SevenTapDetector(onAdminGateTriggered = { onTriggeredLatest() })
    }
    return this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    val xDp = with(density) { offset.x.toDp().value }
                    val yDp = with(density) { offset.y.toDp().value }
                    val stage = detector.onTap(xDp, yDp, nowMillis())
                    when (stage) {
                        SevenTapDetector.Stage.Light ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SevenTapDetector.Stage.Medium ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SevenTapDetector.Stage.Success ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        },
    )
}
