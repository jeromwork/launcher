package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * Closed enumeration of slot kinds — `/config.flows[].slots[].kind` discriminator.
 *
 * Wire-format checklist CHK009: unknown values MUST fail closed (no silent skip).
 * Use [fromWireOrNull] for parsing — returns null for unknown values, caller
 * decides whether to fail-closed (reject document) or partial-apply (record в
 * `/state.partialApplyReasons`).
 *
 * Spec 008 introduces three kinds. Spec 010 / 012 / 013 may add more (additive
 * — adding values doesn't bump schemaVersion per FR-006).
 */
@Serializable
enum class SlotKind(val wireValue: String) {
    /** Phone call — args.contactId references /config.contacts[].id. */
    Call(wireValue = "call"),

    /** SMS — args.contactId references /config.contacts[].id. */
    Sms(wireValue = "sms"),

    /** Open Android app — args.packageName specifies target. */
    OpenApp(wireValue = "open-app"),
    ;

    companion object {
        /**
         * Permissive lookup by wire value. `null` indicates unknown kind —
         * caller chooses behaviour (PartialReason.UNKNOWN_SLOT_KIND или reject).
         */
        fun fromWireOrNull(value: String?): SlotKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}
