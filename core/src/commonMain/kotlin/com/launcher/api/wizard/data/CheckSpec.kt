package com.launcher.api.wizard.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Declarative spec for "is this setting applied?" check. Replaces the
 * hardcoded `when (settingId)` dispatch in [com.launcher.adapters.wizard.AndroidSystemSettingAdapter]
 * with a registry keyed on the variant class.
 *
 * Wire format: polymorphic JSON with discriminator field `kind`. See
 * `specs/task-7-simple-launcher-first-run/contracts/system-settings-pool-v2.md` §4.
 *
 * Cross-platform: kept in commonMain so future iOS / Android-TV adapter
 * modules add their own variants additively without touching engine code
 * (Article VII §15 multi-platform seam).
 */
// TODO(TASK-73): CheckSpec variants below are vendor-blind. On Xiaomi MIUI /
// Huawei EMUI (no GMS) / Samsung One UI the same variant may need
// vendor-specific dispatch (different intent action, different permission
// namespace, or throws). Plan: add optional `perVendor: Map<VendorProfile, CheckSpec>?`
// override + VendorProfile-driven dispatch in CheckHandler pipeline. See
// TASK-73 spec for the full vendor matrix.
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed class CheckSpec {

    @Serializable
    @SerialName("android-role")
    data class AndroidRole(val role: String) : CheckSpec()

    @Serializable
    @SerialName("android-permission")
    data class AndroidPermission(val permission: String) : CheckSpec()

    @Serializable
    @SerialName("android-special-permission")
    data class AndroidSpecialPermission(val variant: String) : CheckSpec()

    @Serializable
    @SerialName("android-accessibility-service")
    data class AndroidAccessibilityService(val componentName: String? = null) : CheckSpec()

    @Serializable
    @SerialName("android-package-home")
    data class AndroidPackageHome(val packageName: String? = null) : CheckSpec()

    @Serializable
    @SerialName("ui-font")
    data class UIFont(val minScale: Float) : CheckSpec()
}
