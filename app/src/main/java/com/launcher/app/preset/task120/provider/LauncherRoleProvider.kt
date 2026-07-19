package com.launcher.app.preset.task120.provider

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Vendor
import com.launcher.preset.model.VendorOverride
import com.launcher.preset.port.Provider
import com.launcher.preset.port.VendorDetector
import com.launcher.preset.port.VendorRecipeSource

/**
 * T031 — LauncherRoleProvider (FR-002, US-2). Vendor-aware since TASK-73
 * (FR-001..FR-004, FR-012).
 *
 * ACL wrapper around Android's `ROLE_HOME` mechanism (CLAUDE.md rule 2). Domain
 * never sees `RoleManager`, `Intent`, `Context`.
 *
 * - `check()` uses `RoleManager.isRoleHeld(ROLE_HOME)` on API ≥ 29; on API 26-28
 *   it falls back to resolving `Intent.ACTION_MAIN + CATEGORY_HOME` and comparing
 *   the resolved package against our own. No vendor branching here (plan.md §3
 *   — the existing null-safe calls already never throw, FR-004 is met as-is).
 * - `apply()` fallback order (TASK-73 FR-003): vendor-specific intent (from
 *   [vendorRecipes], keyed by [vendorDetector]'s current [Vendor]) → the
 *   pre-TASK-73 generic path (unchanged) → `Outcome.Failed(FailReason.InternalError
 *   (fallbackTextKey))` if nothing resolves. `Provider` never shows UI itself
 *   (research.md R2) — the existing `ApplyResult.Failed` Settings path (TASK-69)
 *   already surfaces this text.
 * - Huawei branch (Clarifications #3): [gmsAvailability] is consulted only when
 *   [vendorDetector] reports [Vendor.Huawei] — RoleManager is documented as
 *   unreliable on Huawei devices without Google Play Services (OEM Matrix), so
 *   that specific combination skips the generic RoleManager path entirely
 *   rather than risking an undefined platform call. Huawei devices that do
 *   carry GMS (pre-2019 bans) take the normal generic path, same as any vendor.
 *
 * The current foreground Activity (if available) is preferred as launch source
 * so that the dialog appears as a normal foreground affordance; otherwise we
 * launch from the Application context with `FLAG_ACTIVITY_NEW_TASK`.
 */
class LauncherRoleProvider(
    private val context: Context,
    private val currentActivity: () -> Activity? = { null },
    private val vendorDetector: VendorDetector,
    private val vendorRecipes: VendorRecipeSource,
    private val gmsAvailability: GmsAvailabilityPort,
) : Provider<Component.LauncherRole> {

    override suspend fun check(component: Component.LauncherRole, profile: Profile): Outcome {
        val held = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            isCurrentDefaultHome()
        }
        val outcome = if (held) Outcome.Ok else Outcome.NeedsApply
        if (outcome !is Outcome.Ok) {
            // FR-012 — structured, PII-free diagnostic log for OEM-regression triage.
            Log.d(TAG, "dispatch: vendor=${vendorDetector.detect()} componentType=$COMPONENT_TYPE outcome=$outcome")
        }
        return outcome
    }

    override suspend fun apply(component: Component.LauncherRole, profile: Profile): Outcome {
        // Idempotent: if already default, no dialog needed.
        val alreadyDefault = when (check(component, profile)) {
            Outcome.Ok -> true
            else -> false
        }
        if (alreadyDefault) return Outcome.Ok

        val vendor = vendorDetector.detect()
        val override = vendorRecipes.loadCatalogue()
            .entries[COMPONENT_TYPE]
            ?.get(vendor.name)

        val vendorIntent = override?.toIntentOrNull()
        if (vendorIntent != null && resolves(vendorIntent)) {
            return launch(vendorIntent).orHonestFailure(override)
        }

        val skipGenericPath = vendor == Vendor.Huawei && gmsAvailability.status() !is GmsStatus.Available
        if (skipGenericPath) {
            Log.w(TAG, "Huawei device without GMS — skipping generic RoleManager path (unreliable on this OEM)")
            return honestFailure(override)
        }

        return applyGenericPath().orHonestFailure(override)
    }

    /** Pre-TASK-73 path — unchanged construction + launch logic. */
    private fun applyGenericPath(): Outcome {
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
        return launch(intent)
    }

    private fun launch(intent: Intent): Outcome {
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

    /** FR-012 — structured, PII-free diagnostic log on every fallback-to-failure. */
    private fun honestFailure(override: VendorOverride?): Outcome.Failed {
        val messageKey = override?.fallbackTextKey ?: FALLBACK_TEXT_KEY_GENERIC
        Log.w(TAG, "dispatch fallback: vendor=${vendorDetector.detect()} componentType=$COMPONENT_TYPE outcome=failed")
        return Outcome.Failed(FailReason.InternalError(messageKey))
    }

    /** Remaps any [Outcome.Failed] to the vendor-aware fallback text (FR-003); passes Ok through as-is. */
    private fun Outcome.orHonestFailure(override: VendorOverride?): Outcome =
        if (this is Outcome.Failed) honestFailure(override) else this

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun VendorOverride.toIntentOrNull(): Intent? {
        val action = intentAction
        val pkg = intentPackage
        val className = intentClassName
        if (action == null && pkg == null && className == null) return null
        val intent = if (action != null) Intent(action) else Intent()
        if (pkg != null && className != null) {
            intent.setClassName(pkg, className)
        } else if (pkg != null) {
            intent.setPackage(pkg)
        }
        intentCategory?.let { intent.addCategory(it) }
        return intent
    }

    private fun isCurrentDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    private companion object {
        const val TAG = "LauncherRoleProvider"

        /** Must match [Component.LauncherRole]'s `@SerialName` discriminator. */
        const val COMPONENT_TYPE = "LauncherRole"
        const val FALLBACK_TEXT_KEY_GENERIC = "launcher_role.fallback.generic"
    }
}
