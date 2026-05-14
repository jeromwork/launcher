package com.launcher.api.config

/**
 * Difference between two [ConfigDocument] snapshots (typically local-pending vs
 * server-current after a conflict).
 *
 * Used by:
 *  - FR-051 — Merge UI shows per-element diff for user resolution;
 *  - FR-052 — empty diff → auto-resolve (no UI shown);
 *  - FR-053 — non-overlapping → auto-merge default;
 *  - SC-007 — diff/merge correctness tests.
 *
 * Element matching by [ElementId], NOT by array position — rename of a slot
 * doesn't show up as «delete + add» (FR-004). Match logic в [compute].
 *
 * Top-level mutables omitted intentionally — `presetId` change of the document
 * is treated as «no overlap» (changes только если both editors change it,
 * which is symmetric: they're discussing the same scalar).
 */
data class ConfigDiff(
    val presetIdChanged: PresetIdChange? = null,
    val addedFlows: List<Flow> = emptyList(),
    val removedFlowIds: List<ElementId> = emptyList(),
    val modifiedFlows: List<ModifiedFlow> = emptyList(),
    val addedContacts: List<Contact> = emptyList(),
    val removedContactIds: List<ElementId> = emptyList(),
    val modifiedContacts: List<ModifiedContact> = emptyList(),
) {
    /** True if no elements differ. [FR-052] case — push proceeds без merge UI. */
    val isEmpty: Boolean
        get() = presetIdChanged == null &&
            addedFlows.isEmpty() && removedFlowIds.isEmpty() && modifiedFlows.isEmpty() &&
            addedContacts.isEmpty() && removedContactIds.isEmpty() && modifiedContacts.isEmpty()

    /**
     * True if changes overlap (same id touched в обоих versions с different content).
     * False if local и server changed only disjoint elements — FR-053 auto-merge case.
     */
    val hasOverlappingChanges: Boolean
        get() = modifiedFlows.isNotEmpty() ||
            modifiedContacts.isNotEmpty() ||
            presetIdChanged != null

    companion object {
        /**
         * Pure function. Compares two [ConfigDocument] snapshots — typically
         * `local` is the editor's pending draft and `server` is the freshly-read
         * server-current after optimistic-concurrency rejection (FR-013).
         *
         * Output describes how to transform `local` to incorporate `server`'s
         * concurrent changes (per-element). [ModifiedFlow] / [ModifiedContact]
         * carry both sides so Merge UI can show «keep mine / keep theirs /
         * keep both».
         */
        fun compute(local: ConfigDocument, server: ConfigDocument): ConfigDiff {
            val flowsDiff = diffFlows(local.flows, server.flows)
            val contactsDiff = diffContacts(local.contacts, server.contacts)
            val presetIdChange =
                if (local.presetId != server.presetId) {
                    PresetIdChange(local = local.presetId, server = server.presetId)
                } else null

            return ConfigDiff(
                presetIdChanged = presetIdChange,
                addedFlows = flowsDiff.added,
                removedFlowIds = flowsDiff.removed,
                modifiedFlows = flowsDiff.modified,
                addedContacts = contactsDiff.added,
                removedContactIds = contactsDiff.removed,
                modifiedContacts = contactsDiff.modified,
            )
        }

        private data class FlowsDiff(
            val added: List<Flow>,
            val removed: List<ElementId>,
            val modified: List<ModifiedFlow>,
        )

        private fun diffFlows(local: List<Flow>, server: List<Flow>): FlowsDiff {
            val localById = local.associateBy { it.id }
            val serverById = server.associateBy { it.id }
            val onlyInLocal = localById.keys - serverById.keys
            val onlyInServer = serverById.keys - localById.keys
            val common = localById.keys intersect serverById.keys

            // From server's perspective: "added" = present in server, not in local
            // (server has new ones we don't). "removed" = present in local, not in
            // server (server removed them). This matches "merge server's changes
            // into my local" direction used by merge UI.
            val added = onlyInServer.mapNotNull { serverById[it] }
            val removed = onlyInLocal.toList()
            val modified = common.mapNotNull { id ->
                val l = localById.getValue(id)
                val s = serverById.getValue(id)
                if (l == s) null else ModifiedFlow(id = id, local = l, server = s)
            }
            return FlowsDiff(added = added, removed = removed, modified = modified)
        }

        private data class ContactsDiff(
            val added: List<Contact>,
            val removed: List<ElementId>,
            val modified: List<ModifiedContact>,
        )

        private fun diffContacts(local: List<Contact>, server: List<Contact>): ContactsDiff {
            val localById = local.associateBy { it.id }
            val serverById = server.associateBy { it.id }
            val onlyInServer = (serverById.keys - localById.keys).mapNotNull { serverById[it] }
            val onlyInLocal = (localById.keys - serverById.keys).toList()
            val modified = (localById.keys intersect serverById.keys).mapNotNull { id ->
                val l = localById.getValue(id)
                val s = serverById.getValue(id)
                if (l == s) null else ModifiedContact(id = id, local = l, server = s)
            }
            return ContactsDiff(added = onlyInServer, removed = onlyInLocal, modified = modified)
        }
    }
}

/** [presetId] mismatched между local snapshot and server-current. */
data class PresetIdChange(val local: String, val server: String)

/** A flow with the same [id] differs между local and server. Both sides exposed для merge UI. */
data class ModifiedFlow(
    val id: ElementId,
    val local: Flow,
    val server: Flow,
)

/** A contact with the same [id] differs. */
data class ModifiedContact(
    val id: ElementId,
    val local: Contact,
    val server: Contact,
)
