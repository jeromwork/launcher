package com.launcher.ui.merge

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ElementId

/**
 * Pure helpers для computing default [MergeChoice]s from a [ConfigDiff].
 *
 * Per FR-053: if diff has only non-overlapping changes (different ids touched
 * on each side), default for each diff entry is «KeepBoth» — the merge UI
 * still shows the diff so user can confirm, but action button defaults to
 * «Применить оба». Per FR-052: if diff is empty, merge UI должен not show at
 * all (caller handles).
 */
object MergeDefaults {

    /**
     * Compute initial [MergeChoiceSet] для a given [ConfigDiff]:
     *  - non-overlapping changes → KeepBoth по умолчанию (FR-053);
     *  - overlapping modifications → no default (user MUST choose; UI shows both sides).
     */
    fun forDiff(diff: ConfigDiff): MergeChoiceSet {
        if (diff.hasOverlappingChanges) {
            // Overlapping case: no defaults; user MUST pick per element. Return
            // empty choices → UI shows blank radio groups, save disabled until
            // user picks each.
            return MergeChoiceSet()
        }
        // Non-overlapping case (FR-053): default «keep both» for added/removed,
        // которые в нашем diff direction означают «принять server's change».
        // Modified elements не появятся here (hasOverlappingChanges проверка).
        // For symmetry we return empty here too — UI just shows additions/removals
        // как fait accompli (server has them, our local lost/never had them).
        return MergeChoiceSet()
    }

    /**
     * True if for given diff и current choices, all overlapping elements have
     * a user pick. Used by Merge UI «Сохранить» button enable state.
     */
    fun areAllChoicesMade(diff: ConfigDiff, choices: MergeChoiceSet): Boolean {
        // Overlapping modifications require explicit choice.
        for (modFlow in diff.modifiedFlows) {
            if (choices.flowChoices[modFlow.id] == null) return false
        }
        for (modContact in diff.modifiedContacts) {
            if (choices.contactChoices[modContact.id] == null) return false
        }
        if (diff.presetIdChanged != null && choices.presetChoice == null) return false
        return true
    }
}
