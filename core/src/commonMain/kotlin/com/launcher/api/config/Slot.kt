package com.launcher.api.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * One actionable cell inside a Flow (spec 008 §FR-003, contracts/config.md).
 *
 * - [id]: stable UUID v4 — used for diff/merge identity (FR-051).
 * - [kind]: closed enum [SlotKind]; unknown kinds in incoming wire data → fail closed
 *   (wire-format checklist CHK009).
 * - [args]: opaque to diff — kind-specific structure (e.g. `{"contactId": "..."}`).
 *   Stored as `JsonObject` so adapters can validate per-kind без leaking sealed
 *   shape across the wire-format boundary.
 */
@Serializable
data class Slot(
    val id: ElementId,
    val kind: SlotKind,
    val args: JsonObject? = null,
)
