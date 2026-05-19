package com.launcher.ui.gate

import kotlin.math.abs

/**
 * Spec 010 T094 — pure-logic 7-tap detector (FR-021).
 *
 * Constraints enforced (а / b / c из FR-021):
 *  - **(a)** Caller filters out tile/non-interactive regions before invoking
 *    [onTap]; the detector itself is location-agnostic except for the delta
 *    check (b). Tile attachment lives в the Compose layer (HomeScreen attaches
 *    the gesture to the empty area between BottomFlowBar и tiles).
 *  - **(b)** Каждый последующий tap MUST лежать в пределах ±48 dp по X *и* Y от
 *    **первого** tap (one-point gesture; не cumulative).
 *  - **(c)** Полное окно — 5 секунд. Седьмой tap, попадающий за пределы окна,
 *    сбрасывает счётчик до 1.
 *
 * **No haptic** в этой class — escalation вызывается UI layer'ом (FR-021 даёт
 * 3-уровневую vibration через `LocalHapticFeedback`). Detector returns
 * [Stage] для каждого тапа: UI выбирает соответствующую vibration constant.
 *
 * **Threading**: callers (HomeScreen pointerInput coroutine) MUST вызывать
 * [onTap] на single thread; the detector is not thread-safe by design (one
 * Compose pointer input scope per host).
 *
 * @property deltaDp delta tolerance vs. first tap (Both axes). Defaults to
 *   48 dp per FR-021.
 * @property windowMs sliding window длиной (FR-021c). Defaults to 5000 ms.
 * @property requiredTaps tap count goal. Defaults to 7. Тесты могут понизить
 *   для скорости (e.g., 3).
 */
class SevenTapDetector(
    private val deltaDp: Float = 48f,
    private val windowMs: Long = 5_000L,
    private val requiredTaps: Int = 7,
    private val onAdminGateTriggered: () -> Unit,
) {

    private var firstTapXdp: Float = 0f
    private var firstTapYdp: Float = 0f
    private var firstTapAtMs: Long = 0L
    private var tapCount: Int = 0

    /**
     * Returns the haptic stage that the host should fire for this tap.
     * If this tap caused tap-count to reach [requiredTaps], the detector
     * also invokes [onAdminGateTriggered] **before** returning [Stage.Success]
     * AND resets internal state so the next 7-tap chain starts fresh.
     */
    fun onTap(xDp: Float, yDp: Float, nowMs: Long): Stage {
        val out = computeStage(xDp, yDp, nowMs)
        if (out == Stage.Success) {
            onAdminGateTriggered()
            reset()
        }
        return out
    }

    /** Explicit reset hook for hosts that need to clear state on screen change. */
    fun reset() {
        firstTapXdp = 0f
        firstTapYdp = 0f
        firstTapAtMs = 0L
        tapCount = 0
    }

    private fun computeStage(xDp: Float, yDp: Float, nowMs: Long): Stage {
        val isFresh = tapCount == 0 ||
            (nowMs - firstTapAtMs) > windowMs ||
            abs(xDp - firstTapXdp) > deltaDp ||
            abs(yDp - firstTapYdp) > deltaDp
        if (isFresh) {
            firstTapXdp = xDp
            firstTapYdp = yDp
            firstTapAtMs = nowMs
            tapCount = 1
        } else {
            tapCount += 1
        }
        return when {
            tapCount >= requiredTaps -> Stage.Success
            tapCount <= 3 -> Stage.Light
            tapCount <= 6 -> Stage.Medium
            else -> Stage.Medium  // unreachable given requiredTaps=7 default.
        }
    }

    /**
     * Spec 010 FR-021 escalation ladder.
     *  - [Light] — taps 1-3; UI fires `HapticFeedbackType.VIRTUAL_KEY` /
     *    Compose equivalent `HapticFeedbackType.TextHandleMove`.
     *  - [Medium] — taps 4-6; UI fires `HapticFeedbackType.LONG_PRESS`.
     *  - [Success] — final tap; UI fires success pattern AND navigates to
     *    [ChallengeGateScreen].
     */
    enum class Stage { Light, Medium, Success }
}
