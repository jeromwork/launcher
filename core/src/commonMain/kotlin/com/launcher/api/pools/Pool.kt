package com.launcher.api.pools

import com.launcher.api.preset.Criticality
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlinx.serialization.Serializable

/**
 * Catalog of pool entries available to preset authors. A [Pool] is identified
 * by [id] and versioned independently of any consuming preset.
 *
 * Preset authors copy an entry into [com.launcher.api.preset.Config] at
 * preset-build time, freezing the snapshot (FR-001 self-containment).
 */
@Serializable
data class Pool(
    val id: String,
    val schemaVersion: Int,
    val entries: List<PoolEntry> = emptyList(),
)

@Serializable
data class PoolEntry(
    val id: String,
    val title: String,
    val description: String,
    val check: CheckSpec,
    val apply: ApplySpec,
    val criticality: Criticality,
    val defaultValue: String? = null,
    val deprecated: Boolean = false,
)
