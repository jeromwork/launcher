package com.launcher.api.media

/**
 * Spec 012 — unified picker result. Caller никогда не видит URI / Intent / ContentResolver.
 *
 * Anti-Corruption Layer per CLAUDE.md rule 2: adapter (SystemPhotoPickerAdapter) сам
 * читает URI → byte stream → возвращает [bytes] независимо от Android API level.
 *
 * Fields:
 *  - [bytes]: raw file bytes (после decompression если применимо).
 *  - [mimeType]: content type ("image/jpeg", "image/png", ...).
 *  - [sourceLabel]: optional display hint ("WhatsApp", "Camera") для debug/log — не PII.
 *
 * Task: T1210 (Phase 2). FR-007.
 */
data class MediaPickResult(
    val bytes: ByteArray,
    val mimeType: String,
    val sourceLabel: String? = null,
) {
    // ByteArray equals/hashCode требует ручной реализации.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaPickResult) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false
        if (sourceLabel != other.sourceLabel) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + (sourceLabel?.hashCode() ?: 0)
        return result
    }
}
