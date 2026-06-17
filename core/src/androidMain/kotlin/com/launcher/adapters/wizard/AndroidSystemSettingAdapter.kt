package com.launcher.adapters.wizard

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.SystemSettingPort
import com.launcher.api.wizard.UserPreferencesStore
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.SystemSettingEntry
import com.launcher.api.wizard.data.WireSettingMechanism

/**
 * Android adapter for SystemSettingPort — dispatches per mechanism
 * (FR-055). Reads the bundled `android-pool.json` via ConfigSource lazily
 * (first call only).
 *
 * Per-mechanism handling:
 *  - StandardPermission → PermissionRequestPort.request()
 *  - SpecialPermission → Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS etc.
 *  - AccessibilityService → Settings.ACTION_ACCESSIBILITY_SETTINGS deep-link
 *  - DeepLink → entry.deepLink (e.g. RoleManager.createRequestRoleIntent)
 *  - InAppOnly → in-app toggle (SelfAttest path)
 *
 * TODO(physical-device): OEM-specific quirks (Samsung KNOX accessibility
 * restrictions, Xiaomi MIUI battery whitelist, Huawei EMUI protected apps).
 */
class AndroidSystemSettingAdapter(
    private val context: Context,
    private val configSource: ConfigSource,
    private val permissionRequestPort: PermissionRequestPort,
    @Suppress("unused") private val userPreferencesStore: UserPreferencesStore,
) : SystemSettingPort {

    private var poolCache: List<SystemSettingEntry>? = null

    private suspend fun pool(): List<SystemSettingEntry> {
        poolCache?.let { return it }
        val summaries = configSource.list(ConfigKind.SystemSettingsPool)
        val collected = mutableListOf<SystemSettingEntry>()
        for (summary in summaries) {
            when (val result = configSource.load(ConfigKind.SystemSettingsPool, summary.id)) {
                is ConfigSourceResult.Success -> {
                    val doc = result.document
                    if (doc is ConfigDocument.SystemSettingsPoolDoc) {
                        collected += doc.body.settings
                    }
                }
                else -> { /* ignore failures here — engine surfaces IncompatibleVersion separately */ }
            }
        }
        poolCache = collected
        return collected
    }

    override suspend fun status(settingId: String): SettingStatus {
        val entry = pool().firstOrNull { it.id == settingId }
            ?: return SettingStatus.NotSupportedOnPlatform
        return when (entry.mechanism) {
            WireSettingMechanism.StandardPermission -> {
                if (permissionRequestPort.isGranted(settingId)) SettingStatus.Applied
                else SettingStatus.NotApplied
            }
            WireSettingMechanism.DeepLink -> isDeepLinkApplied(settingId)
            WireSettingMechanism.SpecialPermission -> isSpecialPermissionApplied(settingId)
            WireSettingMechanism.AccessibilityService -> isAccessibilityApplied(settingId)
            WireSettingMechanism.InAppOnly -> SettingStatus.Indeterminate
        }
    }

    override suspend fun applyOrPrompt(settingId: String): ApplyResult {
        val entry = pool().firstOrNull { it.id == settingId }
            ?: return ApplyResult.UnsupportedMechanism
        return when (entry.mechanism) {
            WireSettingMechanism.StandardPermission -> {
                when (permissionRequestPort.request(settingId)) {
                    PermissionResult.Granted -> ApplyResult.Applied
                    PermissionResult.Denied -> ApplyResult.Denied
                    PermissionResult.PermanentlyDenied -> ApplyResult.PermanentlyDenied
                }
            }
            WireSettingMechanism.DeepLink -> handleDeepLink(settingId)
            WireSettingMechanism.SpecialPermission -> launchSpecialPermission(settingId)
            WireSettingMechanism.AccessibilityService -> launchAccessibilitySettings()
            WireSettingMechanism.InAppOnly -> ApplyResult.PromptShown
        }
    }

    private fun handleDeepLink(settingId: String): ApplyResult {
        return when (settingId) {
            "android.role.home" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val rm = context.getSystemService(RoleManager::class.java)
                    val intent = rm?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        return runCatching { context.startActivity(intent); ApplyResult.PromptShown }
                            .getOrElse { ApplyResult.Failed(it.message ?: "startActivity failed") }
                    }
                }
                ApplyResult.UnsupportedMechanism
            }
            else -> ApplyResult.UnsupportedMechanism
        }
    }

    private fun launchSpecialPermission(settingId: String): ApplyResult {
        val intent = when (settingId) {
            "android.battery.ignore_optimizations" -> Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            )
            else -> return ApplyResult.UnsupportedMechanism
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); ApplyResult.PromptShown }
            .getOrElse { ApplyResult.Failed(it.message ?: "startActivity failed") }
    }

    private fun launchAccessibilitySettings(): ApplyResult {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); ApplyResult.PromptShown }
            .getOrElse { ApplyResult.Failed(it.message ?: "startActivity failed") }
    }

    private fun isDeepLinkApplied(settingId: String): SettingStatus = when (settingId) {
        "android.role.home" -> {
            val pm: PackageManager = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved: ComponentName? = pm.resolveActivity(intent, 0)?.activityInfo
                ?.let { ComponentName(it.packageName, it.name) }
            if (resolved?.packageName == context.packageName) SettingStatus.Applied else SettingStatus.NotApplied
        }
        else -> SettingStatus.Indeterminate
    }

    private fun isSpecialPermissionApplied(settingId: String): SettingStatus = when (settingId) {
        "android.battery.ignore_optimizations" -> {
            val pm = context.getSystemService(android.os.PowerManager::class.java)
            if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) SettingStatus.Applied
            else SettingStatus.NotApplied
        }
        else -> SettingStatus.Indeterminate
    }

    @Suppress("UNUSED_PARAMETER")
    private fun isAccessibilityApplied(settingId: String): SettingStatus {
        // OEM-specific reliable detection is non-trivial; the AccessibilityService
        // entry uses Indeterminate detectionStrategy in the bundled pool → SelfAttest.
        return SettingStatus.Indeterminate
    }
}
