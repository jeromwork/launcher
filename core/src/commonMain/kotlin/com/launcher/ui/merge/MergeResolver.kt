package com.launcher.ui.merge

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.Contact
import com.launcher.api.config.ElementId
import com.launcher.api.config.Flow

/**
 * Pure logic: given the local pending, the freshly-read server, the computed
 * [ConfigDiff], and the user's [MergeChoiceSet] — produce the merged
 * [ConfigDocument] that the editor will push.
 *
 * Spec 008 Phase 9 T112/T113 — auto-merge for FR-052 (empty diff) and FR-053
 * (non-overlapping) cases handled by callers; this object only does the
 * mechanical assembly once choices are known.
 */
object MergeResolver {

    /**
     * Resolve the diff to a final [ConfigDocument].
     *
     *  - Non-overlapping additions/removals: server's version wins (we adopt
     *    its added flows/contacts and drop our removed ones).
     *  - Modifications: per user choice.
     *  - Result inherits server's `serverUpdatedAt` and `lastWriterDeviceId`
     *    placeholders — caller (ConfigEditor) overrides on push.
     */
    fun resolve(
        local: ConfigDocument,
        server: ConfigDocument,
        diff: ConfigDiff,
        choices: MergeChoiceSet,
    ): ConfigDocument {
        // Start from local's content, then apply the merge.
        val resolvedPresetId = when {
            diff.presetIdChanged == null -> local.presetId
            choices.presetChoice == MergeChoice.KeepLocal -> local.presetId
            choices.presetChoice == MergeChoice.KeepServer -> server.presetId
            else -> server.presetId // default if user didn't pick
        }

        // Flows: keep local's, applying overrides.
        val resolvedFlows = mergeFlows(
            local = local.flows,
            server = server.flows,
            diff = diff,
            choices = choices,
        )

        // Contacts: same pattern.
        val resolvedContacts = mergeContacts(
            local = local.contacts,
            server = server.contacts,
            diff = diff,
            choices = choices,
        )

        return local.copy(
            presetId = resolvedPresetId,
            flows = resolvedFlows,
            contacts = resolvedContacts,
            // serverUpdatedAt placeholder — adapter overrides on push.
        )
    }

    private fun mergeFlows(
        local: List<Flow>,
        server: List<Flow>,
        diff: ConfigDiff,
        choices: MergeChoiceSet,
    ): List<Flow> {
        val result = mutableListOf<Flow>()
        val localById = local.associateBy { it.id }
        val serverById = server.associateBy { it.id }
        val removedIds = diff.removedFlowIds.toSet()
        val modifiedIds = diff.modifiedFlows.map { it.id }.toSet()

        // For each local element: keep, unless it was «removed-from-server»
        // (i.e., server doesn't have it but local does → server removed,
        // default = accept removal → drop from result).
        // BUT if user chose KeepLocal for a modified id → keep local version.
        for (lf in local) {
            if (lf.id in removedIds) {
                // Server removed; we have it. Default = accept removal (drop).
                // No user choice mechanism для add/remove conflict yet — keep
                // simple: server wins for removals.
                continue
            }
            if (lf.id in modifiedIds) {
                when (choices.flowChoices[lf.id]) {
                    MergeChoice.KeepLocal -> result.add(lf)
                    MergeChoice.KeepServer -> serverById[lf.id]?.let { result.add(it) }
                    MergeChoice.KeepBoth, null -> serverById[lf.id]?.let { result.add(it) } // fallback: server
                }
                continue
            }
            result.add(lf)
        }

        // Added (server has, local doesn't) — accept all per spec 008 default.
        for (af in diff.addedFlows) {
            result.add(af)
        }
        return result
    }

    private fun mergeContacts(
        local: List<Contact>,
        server: List<Contact>,
        diff: ConfigDiff,
        choices: MergeChoiceSet,
    ): List<Contact> {
        val result = mutableListOf<Contact>()
        val serverById = server.associateBy { it.id }
        val removedIds = diff.removedContactIds.toSet()
        val modifiedIds = diff.modifiedContacts.map { it.id }.toSet()

        for (lc in local) {
            if (lc.id in removedIds) continue
            if (lc.id in modifiedIds) {
                when (choices.contactChoices[lc.id]) {
                    MergeChoice.KeepLocal -> result.add(lc)
                    MergeChoice.KeepServer -> serverById[lc.id]?.let { result.add(it) }
                    MergeChoice.KeepBoth, null -> serverById[lc.id]?.let { result.add(it) }
                }
                continue
            }
            result.add(lc)
        }
        for (ac in diff.addedContacts) {
            result.add(ac)
        }
        return result
    }
}
