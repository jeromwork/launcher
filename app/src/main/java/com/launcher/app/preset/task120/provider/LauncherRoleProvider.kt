package com.launcher.app.preset.task120.provider

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

/**
 * T031 — LauncherRoleProvider (FR-002, US-2).
 *
 * ACL wrapper around Android's `ROLE_HOME` mechanism (CLAUDE.md rule 2). Domain
 * never sees `RoleManager`, `Intent`, `Context`.
 *
 * - `check()` uses `RoleManager.isRoleHeld(ROLE_HOME)` on API ≥ 29; on API 26-28
 *   it falls back to resolving `Intent.ACTION_MAIN + CATEGORY_HOME` and comparing
 *   the resolved package against our own.
 * - `apply()` fires the system role-request dialog once. The dialog itself is
 *   idempotent — if already default, the system dismisses immediately.
 *
 * The current foreground Activity (if available) is preferred as launch source
 * so that the dialog appears as a normal foreground affordance; otherwise we
 * launch from the Application context with `FLAG_ACTIVITY_NEW_TASK`.
 */
class LauncherRoleProvider(
    private val context: Context,
    private val currentActivity: () -> Activity? = { null },
) : Provider<Component.LauncherRole> {

    override suspend fun check(component: Component.LauncherRole, profile: Profile): Outcome {
        val held = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            isCurrentDefaultHome()
        }
        return if (held) Outcome.Ok else Outcome.NeedsApply
    }

    override suspend fun apply(component: Component.LauncherRole, profile: Profile): Outcome {
        // Idempotent: if already default, no dialog needed.
        val alreadyDefault = when (check(component, profile)) {
            Outcome.Ok -> true
            else -> false
        }
        if (alreadyDefault) return Outcome.Ok

        val intent: Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                ?: return Outcome.Failed(FailReason.PolicyBlocked("role_manager_unavailable"))
            rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            // API 26-28: open system-defaults settings; user picks HOME app.
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
        }

        val launcher = currentActivity() ?: context
        if (launcher !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            launcher.startActivity(intent)
            Outcome.Ok
        } catch (t: Throwable) {
            Outcome.Failed(FailReason.InternalError("launcher_role.dialog_launch_failed"))
        }
    }

    private fun isCurrentDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
