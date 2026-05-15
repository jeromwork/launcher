package com.launcher.api.apps

import com.launcher.api.result.Outcome

/**
 * Port for launching a third-party app by package name (spec 009 FR-034,
 * FR-035, FR-035a). Real adapter (`androidMain`) chains:
 *   1. `PackageManager.getLaunchIntentForPackage(pkg)` → start activity.
 *   2. On null → `market://details?id=<pkg>` (Play Store app, FR-035).
 *   3. On Play app missing → `https://play.google.com/store/apps/details?id=<pkg>`.
 *   4. None resolved → [OpenAppResult.FailedAll].
 *
 * FR-035a — the `<queries>` manifest block MUST contain a generic
 * `MAIN/LAUNCHER` intent so the launch-intent resolver doesn't return
 * null because of Android 11+ package-visibility restrictions. The
 * adapter does not call `getPackageInfo` (which would require explicit
 * per-package query entries).
 */
interface OpenAppDispatcher {

    suspend fun openApp(packageName: String): Outcome<OpenAppResult, Throwable>
}

sealed interface OpenAppResult {
    data object Launched : OpenAppResult
    data object OpenedPlayStore : OpenAppResult
    data object OpenedWebPlayStore : OpenAppResult
    data class FailedAll(val cause: Throwable?) : OpenAppResult
}
