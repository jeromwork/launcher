package com.launcher.api.wizard.data

import com.launcher.api.wizard.Criticality
import kotlinx.serialization.Serializable

@Serializable
data class SystemSettingsPoolBody(
    val platform: String,
    val settings: List<SystemSettingEntry>,
)

@Serializable
data class SystemSettingEntry(
    val id: String,
    val mechanism: WireSettingMechanism,
    val criticality: WireCriticality,
    val canSkip: Boolean = false,
    val deepLink: String? = null,
    val androidMinApi: Int? = null,
    val dependsOn: List<String> = emptyList(),
    val detectionStrategy: WireDetectionStrategy,
    val labelKey: String,
    val descriptionKey: String,
    val extendedInstructionKey: String? = null,
    // New in schemaVersion 2 (TASK-7). v1 entries deserialize with null;
    // adapter falls back to legacy `mechanism + id` dispatch when null.
    // TODO(schema-v3): once all bundled entries migrated, remove
    // mechanism/deepLink/detectionStrategy and require check/apply non-null.
    val check: CheckSpec? = null,
    val apply: ApplySpec? = null,
)

@Serializable
enum class WireSettingMechanism {
    StandardPermission,
    SpecialPermission,
    AccessibilityService,
    DeepLink,
    InAppOnly,
}

@Serializable
enum class WireDetectionStrategy {
    Programmatic,
    SelfAttest,
    Indeterminate,
}

sealed class SettingMechanism {
    data object StandardPermission : SettingMechanism()
    data object SpecialPermission : SettingMechanism()
    data object AccessibilityService : SettingMechanism()
    data object DeepLink : SettingMechanism()
    data object InAppOnly : SettingMechanism()
}

enum class DetectionStrategy { Programmatic, SelfAttest, Indeterminate }

fun WireSettingMechanism.toDomain(): SettingMechanism = when (this) {
    WireSettingMechanism.StandardPermission -> SettingMechanism.StandardPermission
    WireSettingMechanism.SpecialPermission -> SettingMechanism.SpecialPermission
    WireSettingMechanism.AccessibilityService -> SettingMechanism.AccessibilityService
    WireSettingMechanism.DeepLink -> SettingMechanism.DeepLink
    WireSettingMechanism.InAppOnly -> SettingMechanism.InAppOnly
}

fun WireDetectionStrategy.toDomain(): DetectionStrategy = when (this) {
    WireDetectionStrategy.Programmatic -> DetectionStrategy.Programmatic
    WireDetectionStrategy.SelfAttest -> DetectionStrategy.SelfAttest
    WireDetectionStrategy.Indeterminate -> DetectionStrategy.Indeterminate
}
