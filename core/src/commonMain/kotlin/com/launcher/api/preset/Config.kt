package com.launcher.api.preset

import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlinx.serialization.Serializable

/**
 * Embedded snapshot of a pool entry, frozen at preset-build time.
 *
 * A [Config] is self-contained: it carries the [check] and [apply] specs
 * directly so the preset wire format does not depend on a live pool source
 * (FR-001 self-containment).
 *
 * Reference back to the source pool ([poolId] + [poolVersion]) is kept for
 * traceability and future "refresh from pool" actions, but is not required
 * for runtime evaluation.
 */
@Serializable
data class Config(
    val id: String,
    val poolId: String,
    val poolVersion: Int,
    val entryId: String,
    val title: String,
    val description: String,
    val check: CheckSpec,
    val apply: ApplySpec,
    val criticality: Criticality,
    val defaultValue: String? = null,
    val hideInWizard: Boolean = false,
    val showInSettings: Boolean = true,
)

@Serializable
enum class Criticality {
    Required,
    Optional,
}
