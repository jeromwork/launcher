package com.launcher.api.profile

import com.launcher.api.preset.Config
import kotlinx.serialization.Serializable

/**
 * Live state of a single [Config] inside a [ProfileData].
 */
@Serializable
data class SettingEntry(
    val config: Config,
    val state: AppliedState = AppliedState.NotApplied,
)
