package com.launcher.adapters.setup

import android.content.Context
import android.os.PowerManager
import com.launcher.api.setup.CheckStatus
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.api.setup.SetupCheck
import com.launcher.api.setup.Surface

/**
 * Spec 010 T074 — battery-optimization check (FR-018, R5 OEM risk).
 *
 * Reads [PowerManager.isIgnoringBatteryOptimizations]. На Xiaomi MIUI the
 * API can throw `SecurityException` for «restricted apps» — explicit
 * try-catch surfaces this as [CheckStatus.NotConfigured] так as the
 * Xiaomi-specific «autostart» configuration is exactly what the user
 * needs to fix.
 *
 * Criticality is [Criticality.Recommended] — without battery-opt exemption
 * the launcher still works, just risks delayed FCM пуш and WorkManager
 * deferrals.
 *
 * The user-facing «Настроить» path opens `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`
 * (system settings list) per Play Console policy — direct
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted to
 * certain app categories and would fail Play review.
 */
class BatteryOptimizationCheckAdapter(
    private val context: Context,
) : SetupCheck {

    override val id: String = "battery_optimization"
    override val criticality: Criticality = Criticality.Recommended
    override val surfaces: Set<Surface> = setOf(Surface.Settings)

    override suspend fun check(): CheckStatus {
        val pm = context.getSystemService(PowerManager::class.java)
            ?: return CheckStatus.NotConfigured(reason = "power_manager_unavailable")
        return try {
            val ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)
            if (ignoring) CheckStatus.Ok
            else CheckStatus.NotConfigured(reason = "battery_optimization_active")
        } catch (e: SecurityException) {
            // FR-020b R5 risk — Xiaomi MIUI throws SecurityException на
            // restricted apps; SetupCheckEngine catches all exceptions, но
            // we also catch here so the diagnostic event captures the
            // exact OEM-quirk path вместо generic throw.
            CheckStatus.NotConfigured(reason = "battery_optimization_security_exception:${e.message?.take(80)}")
        }
    }

    override fun resolveIntent(): IntentSpec =
        IntentSpec(
            category = "settings.battery_optimization",
            action = "open",
        )
}
