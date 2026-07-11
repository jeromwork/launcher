package com.launcher.app.preset.task120.provider

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

/**
 * T034 — StatusBarPolicyProvider (FR-005, US-6).
 *
 * ACL wrapper around [WindowInsetsControllerCompat] status-bar hiding.
 *
 * - `check()` is stateless: always returns [Outcome.NeedsApply] so that
 *   [apply] runs once per reconcile — the underlying `hide(statusBars())`
 *   call is idempotent, so re-application is safe. This matches the plan
 *   (T034) — the provider does not attempt to observe transient window
 *   state, since kiosk apply happens after wizard finishes.
 * - `apply()` hides the status bar; on Xiaomi (MIUI) falls back to
 *   `WindowManager.LayoutParams.FLAG_FULLSCREEN` because MIUI's
 *   `WindowInsetsController` implementation has been observed unreliable
 *   (see spec.md § MIUI risk).
 *
 * Requires a foreground [Activity] reference — supplied via
 * [currentActivity] lambda so tests can inject fakes and the provider does
 * not depend on Application-scope singletons.
 */
class StatusBarPolicyProvider(
    private val currentActivity: () -> Activity?,
    private val manufacturer: String = Build.MANUFACTURER,
) : Provider<Component.StatusBarPolicy> {

    override suspend fun check(component: Component.StatusBarPolicy, profile: Profile): Outcome =
        Outcome.NeedsApply

    override suspend fun apply(component: Component.StatusBarPolicy, profile: Profile): Outcome {
        val activity = currentActivity()
            ?: return Outcome.Failed(FailReason.InternalError("status_bar_policy.no_activity"))
        val window = activity.window
            ?: return Outcome.Failed(FailReason.InternalError("status_bar_policy.no_window"))
        return try {
            if (isMiui()) {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            } else {
                WindowInsetsControllerCompat(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.statusBars())
            }
            Outcome.Ok
        } catch (t: Throwable) {
            Outcome.Failed(FailReason.InternalError("status_bar_policy.hide_failed"))
        }
    }

    private fun isMiui(): Boolean = manufacturer.equals("Xiaomi", ignoreCase = true)
}
