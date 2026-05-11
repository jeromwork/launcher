package com.launcher.api.wireformat

import com.launcher.api.action.ActionWireFormat
import kotlinx.serialization.json.Json

/**
 * Shared `Json` configuration for all wire-format types в спеке 006
 * ([com.launcher.api.capability.Capability], [com.launcher.api.health.Health],
 * [com.launcher.api.settings.LauncherSettings]). Delegates to spec 005's
 * [ActionWireFormat.json] so the project has a **single** serialisation-policy
 * source of truth.
 *
 * Inherited settings:
 *  - `classDiscriminator = "kind"` — for sealed-class discriminator field name.
 *  - `ignoreUnknownKeys = true` — forward-compat: newer producers may add
 *    fields, older readers don't crash (FR-043).
 *  - `encodeDefaults = false` — null/default fields stay off the wire (smaller
 *    payloads, cleaner diffs).
 *
 * Spec 006 wire formats are flat data classes (no sealed types yet), so the
 * discriminator config is unused but harmless.
 */
object WireFormatJson {
    /** Compact JSON (one line, no whitespace) — used for DataStore persistence. */
    val json: Json = ActionWireFormat.json

    /** Pretty-printed JSON — used for fixture generation and human inspection. */
    val prettyJson: Json = ActionWireFormat.prettyJson
}
