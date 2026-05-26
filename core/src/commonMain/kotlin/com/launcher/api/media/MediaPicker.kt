package com.launcher.api.media

import com.launcher.api.result.Outcome

/**
 * Spec 012 — domain port для выбора media файлов с устройства.
 *
 * Anti-Corruption Layer для system Photo Picker / SAF (per CLAUDE.md rule 2).
 * Возвращает unified bytes — caller никогда не видит Uri / Intent / ContentResolver.
 *
 * Adapter (`SystemPhotoPickerAdapter`, в androidMain) внутри себя выбирает реализацию
 * по API level:
 *   - **33+**: native `ACTION_PICK_IMAGES` Photo Picker, no permission.
 *   - **29-32**: `androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()`
 *     compat picker, no permission.
 *   - **26-28** (minSdk=26): SAF `ActivityResultContracts.OpenDocument()` → URI →
 *     copy bytes to temp app-private file → read → delete temp.
 *
 * Никаких user-facing hint dialog'ов перед picker'ом (per Clarification Q4).
 *
 * Task: T1211 (Phase 2). FR-007, FR-008.
 */
interface MediaPicker {
    /** Type filter — adapter rejects MIME types не соответствующие kind. */
    enum class Kind { Image, Video, Any }

    /** UI mode hint. На некоторых системных picker'ах не имеет эффекта. */
    enum class Mode { Gallery, Folders }

    /**
     * @param kind type filter. Adapter validates MIME (image/star для Kind.Image и т.д.).
     * @param maxItems max files user can select. В spec 012 всегда 1.
     * @param mode UI hint (Gallery = flat timeline; Folders = album navigation).
     * @return Outcome<List<MediaPickResult>, MediaPickerError>. Cancellation = Failure(Cancelled).
     */
    suspend fun pick(
        kind: Kind,
        maxItems: Int = 1,
        mode: Mode = Mode.Gallery,
    ): Outcome<List<MediaPickResult>, MediaPickerError>

    companion object {
        /**
         * Hard cap на размер файла. Matches `EncryptedMediaStorage` Storage Rules cap
         * из спека 011 — admin-side compression target.
         */
        const val SIZE_CAP_BYTES: Long = 500L * 1024L  // 500 KB
    }
}
