package com.launcher.api.wizard.handlers

import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec

/**
 * Per-variant handler that resolves a [CheckSpec] into a current
 * [SettingStatus]. Declarative replacement for the hardcoded
 * `when (settingId)` dispatch.
 *
 * Real impls live in adapter modules (`androidMain` for Android variants;
 * future `iosMain` for iOS variants). Each variant of [CheckSpec] has
 * exactly one handler registered through DI per build.
 *
 * Per data-model.md §1.3 + plan.md Phase 2 (Article VII §15).
 */
interface CheckHandler {
    suspend fun check(spec: CheckSpec): SettingStatus
}
