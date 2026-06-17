package com.launcher.ui.wizard

import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.Clock
import com.launcher.api.wizard.DismissedHintsStore

sealed class HintAnchor {
    data object TopLeft : HintAnchor()
    data object TopRight : HintAnchor()
    data object BottomLeft : HintAnchor()
    data object BottomRight : HintAnchor()
    data object Center : HintAnchor()
}

sealed class HintResult {
    data object Dismissed : HintResult()
    data object AlreadyDismissed : HintResult()
}

/**
 * Tutorial hint manager — owns "show hint, remember dismissal" lifecycle.
 *
 * Per FR-023, FR-024, FR-025. Persists dismissed hint IDs across process
 * restarts via [DismissedHintsStore].
 */
class TutorialHintManager(
    private val dismissedHintsStore: DismissedHintsStore,
    @Suppress("unused") private val stringResolver: StringResolver,
    @Suppress("unused") private val clock: Clock,
) {

    suspend fun isDismissed(hintId: String): Boolean =
        dismissedHintsStore.isDismissed(hintId)

    suspend fun markDismissed(hintId: String) {
        dismissedHintsStore.markDismissed(hintId)
    }

    suspend fun reset(hintId: String) {
        dismissedHintsStore.clear(hintId)
    }
}
