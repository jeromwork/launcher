package com.launcher.api.profile

import kotlinx.serialization.Serializable

/**
 * Runtime applied-state of a [com.launcher.api.preset.Config].
 *
 * [Indeterminate] is the graceful fallback when a [com.launcher.api.wizard.data.CheckSpec]
 * handler throws (Article VII §15) — engine never crashes the wizard.
 */
@Serializable
sealed class AppliedState {

    @Serializable
    data object NotApplied : AppliedState()

    @Serializable
    data object Applied : AppliedState()

    @Serializable
    data class WithValue(val value: String) : AppliedState()

    @Serializable
    data object Indeterminate : AppliedState()
}
