package com.launcher.api.config

import kotlinx.serialization.Serializable

/**
 * A horizontal screen (поток) of slots в Launcher UI. Top-level grouping в
 * `/config/current.flows[]` (spec 008 §FR-003, contracts/config.md).
 *
 * Note: name `Flow` clashes with `kotlinx.coroutines.flow.Flow`. Internal
 * code in `commonMain/api/config/` will need explicit imports / aliases. The
 * domain name матches contract/config.md и spec.md terminology.
 */
@Serializable
data class Flow(
    val id: ElementId,
    val title: String,
    val slots: List<Slot>,
)
