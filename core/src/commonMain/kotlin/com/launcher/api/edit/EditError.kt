package com.launcher.api.edit

/**
 * Domain error variants для tile editing operations (FR-001) и profile
 * selection (FR-008, FR-008b). Sealed для exhaustive `when` в presentation
 * layer (CLAUDE.md rule 6 — Outcome-based, не throwing).
 *
 * Per failure-recovery.md CHK001 — каждый variant имеет explicit presentation
 * handling (snackbar / dialog / placeholder screen).
 */
sealed class EditError {
    /** Invalid newPosition for [TileEditOperation.Move] (out of bounds или negative). */
    data object InvalidPosition : EditError()

    /** Slot with the given id not found in target flow. */
    data class SlotNotFound(val slotId: String) : EditError()

    /** Flow with the given id not found in config. */
    data class FlowNotFound(val flowId: String) : EditError()

    /**
     * Optimistic concurrency conflict detected by `ConfigEditor.pushPending`
     * (спека 008). Resolution per FR-016:
     *  - Admin profile → snackbar «[Обновить] [Перезаписать]».
     *  - Senior profile → silent last-local-write-wins (Q7).
     */
    data object ConcurrentEditConflict : EditError()

    /**
     * Editor lacks permission to edit this target (e.g. admin tries to edit
     * a Managed config without pairing).
     */
    data object NotAuthorized : EditError()

    /**
     * Custom user-created preset (через TODO-FUTURE-PRODUCT-006 Configurator)
     * encountered by [EditUiProfileSelector] per FR-008b. F-014 explicitly
     * refuses rather than silent-fallback to [EditUiProfile.AdminProfile] —
     * forces solution outside this spec (one-way door avoidance per CLAUDE.md
     * rule 3). Support comes with F-2 (Capability Registry Foundation).
     */
    data object ProfileSelectionRequiresCapabilityRegistry : EditError()
}
