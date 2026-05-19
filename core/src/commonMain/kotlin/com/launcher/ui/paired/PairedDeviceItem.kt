package com.launcher.ui.paired

/**
 * Spec 010 T082 — UI-only model for one row в PairedDevicesScreen (FR-030).
 *
 * Domain note: this is **not** a domain type — it's strictly UI presentation.
 * The mapping from [com.launcher.api.link.Link] + admin metadata + locale
 * formatting happens в the presenter (`PairedDevicesPresenter.snapshot()`),
 * keeping the Composable layer dumb (CLAUDE.md rule 1 — UI doesn't reach
 * for domain reasoning).
 *
 * Fields:
 *  - [linkId]: opaque Firestore link id (used as Compose item key + worker key).
 *  - [displayName]: human-readable label («Маша», device model fallback).
 *  - [pairedDateLabel]: pre-formatted short-locale string (DateFormatter
 *    output); empty when the source [Link.createdAt] is 0.
 *  - [role]: which list ([Section]) this row belongs to.
 */
data class PairedDeviceItem(
    val linkId: String,
    val displayName: String,
    val pairedDateLabel: String,
    val role: Section,
) {
    /** Spec 010 FR-029 — two-list partition. */
    enum class Section {
        /** «Кто помогает мне» — current device is Managed. */
        HelpsMe,

        /** «Кому я помогаю» — current device is Admin. */
        IHelp,
    }
}
