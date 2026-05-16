package com.launcher.adapters.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.launcher.api.apps.IconRef
import com.launcher.api.apps.InstalledApp
import com.launcher.api.apps.InstalledAppsCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android adapter for [InstalledAppsCatalog] — wraps
 * `PackageManager.queryIntentActivities(ACTION_MAIN / CATEGORY_LAUNCHER)`
 * (spec 009 FR-034). Filters our own package; sorts alphabetical by
 * label case-insensitive (mirrors fake contract).
 *
 * Konsist gate (T102): `android.content.pm.*` imports stay in this file;
 * commonMain port exposes only [InstalledApp] (CLAUDE.md rule 1).
 *
 * Package visibility note (Android 11+ FR-035a): relies on `<queries>`
 * block in `AndroidManifest.xml` with generic `MAIN/LAUNCHER` intent.
 * Without it, `queryIntentActivities` silently returns empty on Android
 * 11+ targeting SDK 30+ (security CHK-015).
 */
class InstalledAppsCatalogAdapter(
    context: Context,
    private val ownPackageName: String,
) : InstalledAppsCatalog {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    override suspend fun listApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackageName) return@mapNotNull null
                val label = info.loadLabel(packageManager).toString().ifBlank { pkg }
                val iconRes = info.activityInfo.iconResource
                InstalledApp(
                    packageName = pkg,
                    label = label,
                    iconResource = if (iconRes != 0) IconRef(pkg, iconRes) else null,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
