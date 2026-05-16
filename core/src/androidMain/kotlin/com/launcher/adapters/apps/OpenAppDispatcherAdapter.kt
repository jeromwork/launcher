package com.launcher.adapters.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.launcher.api.apps.OpenAppDispatcher
import com.launcher.api.apps.OpenAppResult
import com.launcher.api.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android adapter for [OpenAppDispatcher] (spec 009 FR-034, FR-035, FR-035a).
 * Fallback chain:
 *   1. `getLaunchIntentForPackage(pkg)` → start ⇒ [OpenAppResult.Launched].
 *   2. App not installed → `market://details?id=<pkg>` resolves Play Store
 *      app ⇒ [OpenAppResult.OpenedPlayStore].
 *   3. Play app missing → `https://play.google.com/store/apps/details?id=<pkg>`
 *      ⇒ [OpenAppResult.OpenedWebPlayStore].
 *   4. Nothing resolves ⇒ [OpenAppResult.FailedAll].
 *
 * `<queries>` manifest block (FR-035a): MAIN/LAUNCHER + VIEW/scheme=market
 * is mandatory for the first two probes to succeed on Android 11+.
 */
class OpenAppDispatcherAdapter(
    context: Context,
) : OpenAppDispatcher {

    private val appContext: Context = context.applicationContext

    override suspend fun openApp(packageName: String): Outcome<OpenAppResult, Throwable> =
        withContext(Dispatchers.Main.immediate) {
            // 1. Direct launch.
            val direct = appContext.packageManager.getLaunchIntentForPackage(packageName)
            if (direct != null) {
                if (tryStart(direct.newTask())) {
                    return@withContext Outcome.Success(OpenAppResult.Launched)
                }
            }

            // 2. Play Store app via market:// scheme.
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName"),
            ).newTask()
            if (tryStart(marketIntent)) {
                return@withContext Outcome.Success(OpenAppResult.OpenedPlayStore)
            }

            // 3. Web Play Store fallback.
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
            ).newTask()
            if (tryStart(webIntent)) {
                return@withContext Outcome.Success(OpenAppResult.OpenedWebPlayStore)
            }

            return@withContext Outcome.Success(OpenAppResult.FailedAll(cause = null))
        }

    private fun tryStart(intent: Intent): Boolean = try {
        appContext.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun Intent.newTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
