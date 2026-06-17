package com.launcher.api.wizard

interface SystemSettingPort {
    suspend fun status(settingId: String): SettingStatus
    suspend fun applyOrPrompt(settingId: String): ApplyResult
}

sealed class SettingStatus {
    data object Applied : SettingStatus()
    data object NotApplied : SettingStatus()
    data object Indeterminate : SettingStatus()
    data object NotSupportedOnPlatform : SettingStatus()
    data class CheckFailed(val reason: String) : SettingStatus()
}

sealed class ApplyResult {
    data object Applied : ApplyResult()
    data object PromptShown : ApplyResult()
    data object Denied : ApplyResult()
    data object PermanentlyDenied : ApplyResult()
    data object UnsupportedMechanism : ApplyResult()
    data class Failed(val reason: String) : ApplyResult()
}
