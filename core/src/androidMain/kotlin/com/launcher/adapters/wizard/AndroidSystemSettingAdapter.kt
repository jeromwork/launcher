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
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.SystemSettingEntry
import com.launcher.api.wizard.data.WireSettingMechanism
import com.launcher.api.wizard.handlers.ApplyHandler
import com.launcher.api.wizard.handlers.CheckHandler
import kotlin.reflect.KClass

/**
 * Android adapter for [SystemSettingPort]. Dispatches via two handler
 * registries (`checkHandlers` for state queries, `applyHandlers` for
 * prompt launches), keyed on the spec variant class. Falls back to the
 * legacy `mechanism + settingId` path for pool entries that have not
 * yet been migrated to schemaVersion 2.
 *
 * Results are memoised in [SettingStatusCache] (default TTL 30 s) and
 * invalidated on `Lifecycle.Event.ON_RESUME` via
 * [CacheInvalidatingLifecycleObserver] (FR-021, FR-022).
 *
 * TODO(multiplatform): IosSystemSettingAdapter — TASK-26 / TASK-29 —
 * both ship as new adapters without changing engine, ports, or
 * commonMain CheckSpec sealed class.
 *
 * TODO(physical-device): OEM-specific quirks (Samsung KNOX accessibility
 * restrictions, Xiaomi MIUI battery whitelist, Huawei EMUI protected
 * apps) need real-device verification — see TASK-7 T064/T065.
 */
class AndroidSystemSettingAdapter(
    private val context: Context,
    private val configSource: ConfigSource,
    private val permissionRequestPort: PermissionRequestPort,
    @Suppress("unused") private val userPreferencesStore: UserPreferencesStore,
    private val checkHandlers: Map<KClass<out CheckSpec>, CheckHandler>,
    private val applyHandlers: Map<KClass<out ApplySpec>, ApplyHandler>,
    private val cache: SettingStatusCache,
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
                else -> { /* engine surfaces IncompatibleVersion separately */ }
            }
        }
        poolCache = collected
        return collected
    }

    override suspend fun status(settingId: String): SettingStatus {
        cache.get(settingId)?.let { return it }
        val entry = pool().firstOrNull { it.id == settingId }
            ?: return SettingStatus.NotSupportedOnPlatform.also { cache.put(settingId, it) }
        val status = entry.check?.let { spec ->
            // v2 declarative path.
            val handler = checkHandlers[spec::class]
                ?: return@let SettingStatus.Indeterminate // missing handler in this build
            handler.check(spec)
        } ?: legacyStatus(entry)
        cache.put(settingId, status)
        return status
    }

    override suspend fun applyOrPrompt(settingId: String): ApplyResult {
        val entry = pool().firstOrNull { it.id == settingId }
            ?: return ApplyResult.UnsupportedMechanism
        val result = entry.apply?.let { spec ->
            val handler = applyHandlers[spec::class] ?: return@let ApplyResult.UnsupportedMechanism
            handler.apply(spec)
        } ?: legacyApply(entry)
        cache.invalidate(settingId) // force re-check on next status call
        return result
    }

    // --- Legacy v1 dispatch (TODO(schema-v3): remove once all pool entries migrate to v2) ---

    private suspend fun legacyStatus(entry: SystemSettingEntry): SettingStatus = when (entry.mechanism) {
        WireSettingMechanism.StandardPermission -> {
            if (permissionRequestPort.isGranted(entry.id)) SettingStatus.Applied else SettingStatus.NotApplied
        }
        WireSettingMechanism.DeepLink -> isDeepLinkApplied(entry.id)
        WireSettingMechanism.SpecialPermission -> isSpecialPermissionApplied(entry.id)
        WireSettingMechanism.AccessibilityService -> SettingStatus.Indeterminate
        WireSettingMechanism.InAppOnly -> SettingStatus.Indeterminate
    }

    private suspend fun legacyApply(entry: SystemSettingEntry): ApplyResult = when (entry.mechanism) {
        WireSettingMechanism.StandardPermission -> {
            when (permissionRequestPort.request(entry.id)) {
                PermissionResult.Granted -> ApplyResult.Applied
                PermissionResult.Denied -> ApplyResult.Denied
                PermissionResult.PermanentlyDenied -> ApplyResult.PermanentlyDenied
            }
        }
        WireSettingMechanism.DeepLink -> handleDeepLink(entry.id)
        WireSettingMechanism.SpecialPermission -> launchSpecialPermission(entry.id)
        WireSettingMechanism.AccessibilityService -> launchAccessibilitySettings()
        WireSettingMechanism.InAppOnly -> ApplyResult.PromptShown
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
}
