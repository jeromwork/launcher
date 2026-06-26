package com.launcher.app.settings

import com.launcher.api.localization.StringResolver
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.WizardEngine
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireStepType
import com.launcher.api.wizard.data.WizardManifest

/**
 * Computes the pending-setup checklist shown on the Settings screen
 * (FR-014 / Сценарий 4). Backed by [WizardEngine.computePending],
 * which is the single source of truth for "what does this device
 * still need from the user".
 */
class PendingChecklistViewModel(
    private val engine: WizardEngine,
    private val configSource: ConfigSource,
    @Suppress("unused") private val stringResolver: StringResolver,
) {
    suspend fun load(): PendingChecklistState {
        val result = configSource.load(ConfigKind.WizardManifest, MANIFEST_ID)
        if (result !is ConfigSourceResult.Success) return PendingChecklistState(items = emptyList())
        val doc = result.document as? ConfigDocument.Manifest ?: return PendingChecklistState(emptyList())
        val manifest = WizardManifest(doc.header, doc.body)
        val pending = engine.computePending(manifest)
        return PendingChecklistState(
            items = pending.map { entry ->
                PendingChecklistState.Item(
                    refId = entry.refId,
                    stepType = entry.stepType,
                    labelKey = labelKeyFor(entry.refId, entry.stepType),
                    isRequired = (entry.criticality ?: WireCriticality.Required) == WireCriticality.Required,
                )
            },
        )
    }

    private fun labelKeyFor(refId: String, stepType: WireStepType): String = when (stepType) {
        WireStepType.SystemSetting -> SYSTEM_SETTING_LABEL_KEYS[refId] ?: refId
        WireStepType.UIChoice -> "ui_${refId}_question"
        WireStepType.TutorialHint -> "hint_${refId}"
    }

    private companion object {
        const val MANIFEST_ID = "wizard-manifest.simple-launcher"
        // Hardcoded for the simple-launcher pool today; will move to a
        // pool-driven lookup when the pending screen surfaces other
        // profiles' settings too (out of scope for TASK-7).
        val SYSTEM_SETTING_LABEL_KEYS: Map<String, String> = mapOf(
            "android.role.home" to "system_setting_role_home_label",
            "android.permission.POST_NOTIFICATIONS" to "system_setting_post_notifications_label",
            "android.permission.CALL_PHONE" to "system_setting_call_phone_label",
            "android.accessibility.our-service" to "system_setting_accessibility_label",
            "android.battery.ignore_optimizations" to "system_setting_battery_label",
            "android.hide_status_bar" to "system_setting_hide_status_bar_label",
        )
    }
}

data class PendingChecklistState(val items: List<Item>) {
    data class Item(
        val refId: String,
        val stepType: WireStepType,
        val labelKey: String,
        val isRequired: Boolean,
    )
}
