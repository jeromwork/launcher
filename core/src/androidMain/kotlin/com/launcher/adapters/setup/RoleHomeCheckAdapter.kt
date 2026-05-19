package com.launcher.adapters.setup

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * Spec 010 T037 — real adapter for «есть ли у нас роль launcher'а?» check
 * (FR-018 RoleHomeCheck, Required first).
 *
 * API ≥ 29: [RoleManager.isRoleHeld]([RoleManager.ROLE_HOME]).
 * API 26-28 (Android 8/9 — plan §11 C-6 legacy fallback): no RoleManager;
 * resolve the default `CATEGORY_HOME` activity и сравним с нашим packageName.
 *
 * TODO(plan §11 C-6 + minSdk bump): when minSdk reaches 29 (Android 10+),
 * delete the legacy branch — Android 10 launched 2019-09 so practical floor
 * matures ~2027-2028 per the project's senior-safe install base.
 */
class RoleHomeCheckAdapter(
    private val context: Context,
) : SetupCheck {

    override val id: String = "role_home"
    override val criticality: Criticality = Criticality.Required
    override val surfaces: Set<Surface> = setOf(Surface.Settings)

    override suspend fun check(): CheckStatus =
        if (isRoleHomeHeld()) {
            CheckStatus.Ok
        } else {
            CheckStatus.NotConfigured(reason = "role_home_not_held")
        }

    override fun resolveIntent(): IntentSpec =
        IntentSpec(
            category = "role.home",
            action = "request",
        )

    private fun isRoleHomeHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            // Legacy API 26-28: resolve the default HOME activity и сравним.
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }
}
