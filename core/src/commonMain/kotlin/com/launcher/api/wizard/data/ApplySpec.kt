package com.launcher.api.wizard.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Declarative spec for "how to apply this setting" prompt. Pair to [CheckSpec].
 *
 * Wire format: polymorphic JSON with discriminator field `kind`. See
 * `specs/task-7-simple-launcher-first-run/contracts/system-settings-pool-v2.md` §5.
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
@Serializable
sealed class ApplySpec {

    @Serializable
    @SerialName("standard-permission-request")
    data class StandardPermissionRequest(val permission: String) : ApplySpec()

    @Serializable
    @SerialName("android-role-request")
    data class AndroidRoleRequest(val role: String) : ApplySpec()

    @Serializable
    @SerialName("settings-deep-link")
    data class SettingsDeepLink(val action: String, val packageScoped: Boolean = false) : ApplySpec()

    @Serializable
    @SerialName("in-app-only")
    data object InAppOnly : ApplySpec()
}
