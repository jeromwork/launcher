package com.launcher.ui.health

/**
 * UI severity enum (spec 009 FR-017, FR-019). Drives indicator chip
 * colour, icon, and TalkBack severity prefix.
 *
 * Separate from wire-format `SeverityWire` (api/config/PhoneHealthSettings)
 * — one-way mapping wire → UI. Keeps Compose-side enum ordinals decoupled
 * from wire ordering (CLAUDE.md rule 1; plan §11 C-5).
 */
enum class PhoneHealthSeverity {
    Info,
    Warning,
    Critical,
}
