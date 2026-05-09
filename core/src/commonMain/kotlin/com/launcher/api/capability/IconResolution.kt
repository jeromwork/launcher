package com.launcher.api.capability

/**
 * Result of [IconStorage.resolve]. Tells the UI how to render an icon for a
 * given `iconId`, or that no icon was found.
 *
 * Three variants disambiguate diagnostic categories (FR-052 telemetry):
 *  - [Drawable] resolved successfully.
 *  - [Placeholder] known namespace but resource missing (log category
 *    `missing_resource`) — UI shows generic placeholder.
 *  - [NotFound] unknown namespace OR invalid `iconId` format (log category
 *    `unknown_namespace`) — UI also shows generic placeholder, but diagnostic
 *    bucket is different.
 *
 * **Note on `Drawable.androidResourceId: Int`** — this is the only Android-typed
 * field that leaks into `commonMain` (per research R3, accepted one-way door).
 * Acceptable until iOS implementation is needed; will become `expect class`
 * with platform-specific `actual` (Android resource id vs `UIImage` etc.) at
 * that point.
 */
sealed class IconResolution {
    /** Resolved drawable — UI renders via Android resource id. */
    data class Drawable(val androidResourceId: Int) : IconResolution()

    /** Known namespace, resource missing or not yet downloaded. UI shows generic placeholder. */
    data object Placeholder : IconResolution()

    /** Unknown namespace OR malformed iconId. UI shows generic placeholder; logged separately. */
    data object NotFound : IconResolution()
}
