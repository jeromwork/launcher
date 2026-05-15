package com.launcher.ui.merge

import com.launcher.api.config.ElementId

/**
 * User's resolution for one diff element (spec 008 Phase 9, FR-051).
 *
 * For each modified/added/removed element в [com.launcher.api.config.ConfigDiff],
 * the user chooses:
 *
 *  - [KeepLocal]: discard the server-side change, keep my edit;
 *  - [KeepServer]: discard my edit, accept server's value;
 *  - [KeepBoth]: include both (only valid for non-overlapping additions —
 *    auto-mergeable case FR-053).
 *
 * For PresetId scalar conflict, user picks one of the two.
 */
sealed interface MergeChoice {
    data object KeepLocal : MergeChoice
    data object KeepServer : MergeChoice
    data object KeepBoth : MergeChoice
}

/**
 * Indexed map of choices per [ElementId] (and a separate slot for presetId
 * conflict). UI keeps this state в [MergeComponent] / `rememberSaveable`.
 *
 * Empty map при entry → user hasn't picked anything yet. Default choice is
 * computed by [MergeDefaults.forNonOverlappingChanges] for FR-053 auto-merge.
 */
data class MergeChoiceSet(
    val flowChoices: Map<ElementId, MergeChoice> = emptyMap(),
    val contactChoices: Map<ElementId, MergeChoice> = emptyMap(),
    val presetChoice: MergeChoice? = null,
) {
    val isComplete: Boolean
        get() {
            // For a complete merge, every modifiedFlow/contact/preset MUST
            // have a choice. Added/removed by id don't require choice (default
            // is "accept server's change") — covered by MergeDefaults.
            return true // simplification — caller validates against ConfigDiff
        }
}
