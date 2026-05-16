package com.launcher.api.apps

import kotlinx.serialization.Serializable

/**
 * Port over Android `PackageManager.queryIntentActivities(ACTION_MAIN /
 * CATEGORY_LAUNCHER)` (spec 009 FR-034). Domain side sees only
 * [InstalledApp]; vendor types (`ResolveInfo`, `Drawable`) live in the
 * `androidMain` adapter (CLAUDE.md rule 1).
 */
interface InstalledAppsCatalog {

    /**
     * Enumerate launchable third-party apps. Filters out our own package
     * and sorts by [InstalledApp.label] case-insensitive.
     */
    suspend fun listApps(): List<InstalledApp>
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconResource: IconRef?,
)

/**
 * Port-friendly icon reference. Adapter resolves to `Drawable` /
 * `ImageBitmap`. Domain MUST NOT depend on `android.graphics.*`.
 */
@Serializable
data class IconRef(
    val packageName: String,
    val resourceId: Int,
)
