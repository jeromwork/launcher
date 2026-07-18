package com.launcher.preset.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Apply-state of an [Entity], expressed **as a component in the bag** (canonical
 * ECS, CL-5 / FR-STATE): "no special status fields — state exists only as
 * component data that systems query and modify". Replaces the old
 * `ComponentStatus` enum + `Entity.status` field.
 *
 * A single sealed state component (not a set of state-tags) because apply-state
 * is **mutually exclusive** — an entity is in exactly one state at a time — and
 * [Failed] **carries data** (`reason`). A `Set<Tag>` could not enforce mutual
 * exclusivity (`Applied` + `Failed` at once) nor attach `Failed`'s reason.
 *
 * At most one [LifecycleState] per entity (CL-3) ⇒ `entity.get<LifecycleState>()`
 * is unambiguous; the System ([com.launcher.preset.engine.ReconcileEngine])
 * transitions state by swapping this one component.
 *
 * The 12th member of the closed [Component] set, but a *state* component: no
 * `Provider` (like the structural subtypes) — it resolves to `NoOpProvider`.
 *
 * [Unverifiable] (FR-014) — the setting was applied on the user's word and the OS
 * exposes no read-back (e.g. hiding the system status bar is a chain of intents
 * with no query API). Recording [Applied] there would be a lie: `BootCheck` would
 * trust a fiction, and re-checking on every cold start would nag the user forever.
 * Only the interactive paths (Wizard / RunMode.Single) may record it; `BootCheck`
 * skips such entities entirely.
 */
@Serializable
sealed interface LifecycleState : Component {

    @Serializable
    @SerialName("LifecycleState.Pending")
    data object Pending : LifecycleState

    @Serializable
    @SerialName("LifecycleState.Applied")
    data object Applied : LifecycleState

    @Serializable
    @SerialName("LifecycleState.Skipped")
    data object Skipped : LifecycleState

    @Serializable
    @SerialName("LifecycleState.Unverifiable")
    data object Unverifiable : LifecycleState

    @Serializable
    @SerialName("LifecycleState.Failed")
    data class Failed(val reason: FailReason) : LifecycleState
}
