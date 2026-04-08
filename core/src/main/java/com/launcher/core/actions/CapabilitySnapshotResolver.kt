package com.launcher.core.actions

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.launcher.api.CapabilitySnapshot
import com.launcher.api.CapabilityState
import com.launcher.api.CapabilityStatus
import com.launcher.api.ControlMode
import com.launcher.api.SafetyCapability

class CapabilitySnapshotResolver(
    private val context: Context,
    private val modeStore: ControlModeStore,
    private val accessibilityServiceClassName: String,
) {
    fun resolve(): CapabilitySnapshot {
        val mode = modeStore.get()
        val statuses = listOf(
            CapabilityStatus(SafetyCapability.DEFAULT_HOME, defaultHomeState(), reasonForHome()),
            CapabilityStatus(
                SafetyCapability.ACCESSIBILITY_SERVICE,
                accessibilityState(),
                reasonForAccessibility(),
            ),
            CapabilityStatus(SafetyCapability.USAGE_ACCESS, usageAccessState(), reasonForUsage()),
            CapabilityStatus(SafetyCapability.OVERLAY, overlayState(), reasonForOverlay()),
            CapabilityStatus(
                SafetyCapability.BATTERY_EXEMPTION,
                batteryExemptionState(),
                reasonForBattery(),
            ),
            CapabilityStatus(SafetyCapability.DEVICE_OWNER, deviceOwnerState(), reasonForDeviceOwner()),
            CapabilityStatus(SafetyCapability.LOCK_TASK, lockTaskState(), reasonForLockTask()),
            CapabilityStatus(
                SafetyCapability.STATUS_BAR_RESTRICTION,
                statusBarRestrictionState(mode),
                reasonForStatusBar(mode),
            ),
        )
        return CapabilitySnapshot(controlMode = mode, statuses = statuses)
    }

    private fun defaultHomeState(): CapabilityState {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0) ?: return CapabilityState.MISSING
        return if (resolved.activityInfo?.packageName == context.packageName) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.MISSING
        }
    }

    private fun accessibilityState(): CapabilityState {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!enabled) {
            return CapabilityState.MISSING
        }
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return CapabilityState.MISSING
        val expected = ComponentName(context.packageName, accessibilityServiceClassName)
            .flattenToString()
            .lowercase()
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices.lowercase()) }
        while (splitter.hasNext()) {
            if (splitter.next() == expected) {
                return CapabilityState.GRANTED
            }
        }
        return CapabilityState.MISSING
    }

    private fun usageAccessState(): CapabilityState {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return CapabilityState.MISSING
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return if (mode == AppOpsManager.MODE_ALLOWED) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.MISSING
        }
    }

    private fun overlayState(): CapabilityState =
        if (Settings.canDrawOverlays(context)) CapabilityState.GRANTED else CapabilityState.MISSING

    private fun batteryExemptionState(): CapabilityState {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return CapabilityState.MISSING
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.LIMITED
        }
    }

    private fun deviceOwnerState(): CapabilityState {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return CapabilityState.MISSING
        return if (dpm.isDeviceOwnerApp(context.packageName)) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.MISSING
        }
    }

    private fun lockTaskState(): CapabilityState {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return CapabilityState.MISSING
        return if (dpm.isLockTaskPermitted(context.packageName)) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.LIMITED
        }
    }

    private fun statusBarRestrictionState(mode: ControlMode): CapabilityState {
        if (mode != ControlMode.STRICT) {
            return CapabilityState.LIMITED
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return CapabilityState.MISSING
        }
        return if (deviceOwnerState() == CapabilityState.GRANTED) {
            CapabilityState.GRANTED
        } else {
            CapabilityState.MISSING
        }
    }

    private fun reasonForHome(): String? =
        if (defaultHomeState() == CapabilityState.GRANTED) null else "not_default_home"

    private fun reasonForAccessibility(): String? =
        if (accessibilityState() == CapabilityState.GRANTED) null else "accessibility_not_enabled"

    private fun reasonForUsage(): String? =
        if (usageAccessState() == CapabilityState.GRANTED) null else "usage_access_not_granted"

    private fun reasonForOverlay(): String? =
        if (overlayState() == CapabilityState.GRANTED) null else "overlay_not_granted"

    private fun reasonForBattery(): String? =
        if (batteryExemptionState() == CapabilityState.GRANTED) null else "battery_optimization_active"

    private fun reasonForDeviceOwner(): String? =
        if (deviceOwnerState() == CapabilityState.GRANTED) null else "device_owner_missing"

    private fun reasonForLockTask(): String? =
        if (lockTaskState() == CapabilityState.GRANTED) null else "lock_task_not_permitted"

    private fun reasonForStatusBar(mode: ControlMode): String? =
        if (statusBarRestrictionState(mode) == CapabilityState.GRANTED) null else "status_bar_not_restricted"
}

