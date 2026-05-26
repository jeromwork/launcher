package com.launcher.api.media

/**
 * Spec 012 — categorical picker errors. NOT human-readable strings — UI maps each
 * к localised message (per ux-quality checklist CHK011 + ADR-004).
 *
 * Task: T1210 (Phase 2). FR-009.
 */
sealed interface MediaPickerError {
    /** User dismissed picker без выбора. Not an error per se — caller handles silently. */
    data object Cancelled : MediaPickerError

    /** Выбранный файл имеет MIME type, не соответствующий запрошенному [MediaPicker.Kind]. */
    data class InvalidMimeType(val actual: String, val expected: String) : MediaPickerError

    /** I/O failure при чтении URI (corrupt content provider, permission revoked mid-read). */
    data class IOError(val cause: Throwable) : MediaPickerError

    /** Файл превышает [MediaPicker.SIZE_CAP_BYTES]. */
    data class FileTooLarge(val actualBytes: Long, val maxBytes: Long) : MediaPickerError
}
