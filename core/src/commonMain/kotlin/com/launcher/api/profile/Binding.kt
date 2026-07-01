package com.launcher.api.profile

import kotlinx.serialization.Serializable

/**
 * What is bound to a slot at runtime. Variant fields are mutually-exclusive
 * but kept flat (not sealed) for forward compat — future targets add new
 * optional fields, old clients ignore them gracefully.
 *
 * Clarification #12 hook: [intentExtras] reserved for per-target tweaks.
 */
@Serializable
data class Binding(
    val slotPosition: SlotPosition,
    val targetPackage: String? = null,
    val contactRef: String? = null,
    val url: String? = null,
    val intentExtras: Map<String, String> = emptyMap(),
)

@Serializable
data class SlotPosition(
    val screenId: String,
    val row: Int,
    val col: Int,
)
