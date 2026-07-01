package com.launcher.api.preset

import com.launcher.api.profile.Binding
import com.launcher.api.profile.Layout
import kotlinx.serialization.Serializable

/**
 * Optional initial layout + bindings shipped with a preset.
 *
 * On first activation [com.launcher.api.switchstrategy.CopyOnActivateStrategy]
 * copies this into the active [com.launcher.api.profile.ProfileData].
 * On switch back to a preset that already has profile state, this is ignored.
 */
@Serializable
data class AbstractProfile(
    val layout: Layout,
    val bindings: List<Binding> = emptyList(),
)
