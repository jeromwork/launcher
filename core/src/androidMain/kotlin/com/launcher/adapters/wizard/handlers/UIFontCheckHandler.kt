package com.launcher.adapters.wizard.handlers

import android.content.Context
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler

/**
 * `CheckSpec.UIFont` handler — reads system fontScale from current
 * Configuration. Returns [SettingStatus.Applied] if scale >= [CheckSpec.UIFont.minScale],
 * else [SettingStatus.NotApplied].
 *
 * Per TASK-65 FR-024 — demonstrates engine genericity (a non-permission,
 * non-role check works through the same WizardEngine pipeline).
 */
class UIFontCheckHandler(
    private val context: Context,
) : CheckHandler {
    override suspend fun check(spec: CheckSpec): SettingStatus {
        val req = (spec as? CheckSpec.UIFont) ?: return SettingStatus.NotSupportedOnPlatform
        val scale = context.resources.configuration.fontScale
        return if (scale >= req.minScale) SettingStatus.Applied else SettingStatus.NotApplied
    }
}
