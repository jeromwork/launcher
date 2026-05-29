package com.launcher.api.edit

/**
 * Who/what is being edited. Used by [EditUiProfileSelector] to derive the
 * [EditUiProfile], and by presentation layer to decide visual decorations
 * (remote frame + banner per FR-014 vs self per FR-015).
 *
 * Per Q9 clarification 2026-05-29: entry gesture is determined by [presetId]
 * (target preset), not by editor identity. Workspace target → long-press
 * (FR-005); Simple Launcher target → 7-tap (FR-006).
 *
 * @property linkId pairing link ID (existing спека 007 PairingService),
 *   or the sentinel [LOCAL] when editing the device's own config.
 * @property presetId preset identifier — `"workspace"` / `"simple-launcher"`
 *   / other built-in. Drives profile selection (FR-008) and picker filtering
 *   (FR-019).
 * @property isSelf derived from [linkId]; `true` when editing local target.
 */
data class TargetIdentity(
    val linkId: String,
    val presetId: String,
    val isSelf: Boolean,
) {
    init {
        require(linkId.isNotBlank()) { "linkId must not be blank" }
        require(presetId.isNotBlank()) { "presetId must not be blank" }
        require(isSelf == (linkId == LOCAL)) {
            "isSelf must equal (linkId == LOCAL); got isSelf=$isSelf linkId=$linkId"
        }
    }

    companion object {
        /** Sentinel [linkId] value indicating editing this device's own config. */
        const val LOCAL: String = "local"

        /** Convenience constructor for editing this device's own config. */
        fun forSelf(presetId: String): TargetIdentity =
            TargetIdentity(linkId = LOCAL, presetId = presetId, isSelf = true)

        /** Convenience constructor for editing a paired Managed device's config. */
        fun forRemote(linkId: String, presetId: String): TargetIdentity {
            require(linkId != LOCAL) {
                "forRemote linkId must not be '$LOCAL'; use forSelf() instead"
            }
            return TargetIdentity(linkId = linkId, presetId = presetId, isSelf = false)
        }
    }
}
