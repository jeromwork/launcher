package com.launcher.api.admin

import com.launcher.api.config.ConfigDiff
import com.launcher.api.config.ConfigDocument

/**
 * State held by `EditorScreenViewModel` (spec 009 FR-001, FR-013, FR-015,
 * FR-040). [draft] is the user's in-progress edit (autosaved locally per
 * spec 008 §FR-056); [applied] is the last server-known config;
 * [mergeConflict] set if push failed due to concurrent edit.
 */
data class EditorState(
    val linkId: String,
    val mode: AdminEditorMode,
    val draft: ConfigDocument,
    val applied: ConfigDocument?,
    val pendingPush: Boolean,
    val mergeConflict: MergeConflictState?,
)

/**
 * Spec 008 reuses [ConfigDiff] for diff/merge UI; spec 009 surfaces it
 * through EditorScreen when admin push conflicts with concurrent edit.
 */
data class MergeConflictState(
    val localDraft: ConfigDocument,
    val serverConfig: ConfigDocument,
    val diff: ConfigDiff,
    val detectedAt: Long,
)
