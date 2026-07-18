package com.launcher.preset.engine

import com.launcher.preset.ecs.entity
import com.launcher.preset.ecs.get
import com.launcher.preset.model.ChangeItem
import com.launcher.preset.model.Component
import com.launcher.preset.model.LifecycleState
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.RunMode
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.InteractionSink
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import com.launcher.preset.port.ProviderRegistry

/**
 * Single reconcile engine covering all 4 RunMode branches (FR-010).
 *
 * The ECS "System": it queries the World (`Profile.entities`) and mutates it. It
 * records apply-state **canonically** by swapping the entity's [LifecycleState]
 * component (`profile.setState`), not by writing a `status` field (CL-5).
 *
 * NB: engine MUST NOT `when` on concrete Component subtypes (fitness #2). All
 * subtype-specific logic goes through the ProviderRegistry indirection; the state
 * component resolves to `NoOpProvider` like the structural subtypes.
 */
class ReconcileEngine(
    private val registry: ProviderRegistry,
    private val store: ProfileStore,
) {

    /**
     * @param mode        Wizard / BootCheck / Single / RemotePush
     * @param sink        interactive answers (required for RunMode.Wizard)
     * @param targetId    for RunMode.Single — the component id to reconcile
     * @param changes     for RunMode.RemotePush — the diff to apply
     */
    // TODO(capability-registry): dispatch here will grow the exposure surface for
    // AI/MCP integration in F-2. Keep the switch shallow.
    suspend fun run(
        mode: RunMode,
        sink: InteractionSink? = null,
        targetId: String? = null,
        changes: List<ChangeItem> = emptyList(),
    ): Profile {
        val current = store.load() ?: error("ReconcileEngine.run: profile not initialized")
        return when (mode) {
            RunMode.Wizard -> runWizard(current, sink)
            RunMode.BootCheck -> runBootCheck(current)
            RunMode.Single -> runSingle(current, targetId ?: error("Single mode requires targetId"))
            RunMode.RemotePush -> runRemotePush(current, changes)
        }
    }

    private suspend fun runWizard(initial: Profile, sink: InteractionSink?): Profile {
        var profile = initial
        for (pc in initial.entities.filter { !it.isApplied() }) {
            profile = when (pc.wizardBehavior) {
                WizardBehavior.Interactive -> {
                    val answered = sink?.askUser(pc)
                    if (answered == null) {
                        profile.setState(pc.id, LifecycleState.Skipped)
                    } else {
                        val replaced = profile.with(pc.id, answered)
                        dispatchApply(replaced, pc.id)
                    }
                }
                WizardBehavior.AutoApply -> dispatchApply(profile, pc.id)
                WizardBehavior.InitialDefault -> profile.setState(pc.id, LifecycleState.Applied)
            }
            store.save(profile)
        }
        return profile
    }

    /**
     * T127-017 (FR-014): skips [LifecycleState.Unverifiable] entities. The OS
     * cannot confirm those settings, so re-checking them on every cold start would
     * either re-nag the user forever or silently flip the status back and forth.
     * They are re-verified only on an explicit Settings action ([RunMode.Single]).
     */
    private suspend fun runBootCheck(initial: Profile): Profile {
        var profile = initial
        val candidates = initial.entities
            .filter { it.critical && it.get<LifecycleState>() !is LifecycleState.Unverifiable }
        for (pc in candidates) {
            val checkResult = dispatchCheck(profile, pc.id)
            if (checkResult is Outcome.NeedsApply) {
                profile = dispatchApply(profile, pc.id)
            } else if (checkResult is Outcome.Failed) {
                profile = profile.setState(pc.id, LifecycleState.Failed(checkResult.reason))
            }
            store.save(profile)
        }
        return profile
    }

    private suspend fun runSingle(initial: Profile, targetId: String): Profile {
        val pc = initial.entities.firstOrNull { it.id == targetId }
            ?: return initial
        val result = dispatchApply(initial, pc.id)
        store.save(result)
        return result
    }

    private suspend fun runRemotePush(initial: Profile, changes: List<ChangeItem>): Profile {
        var profile = initial
        for (change in changes) {
            profile = when (change) {
                is ChangeItem.Added -> {
                    val pc = entity(change.id) {
                        component(change.component)
                        wizardBehavior = WizardBehavior.AutoApply
                        critical = false
                        component(LifecycleState.Pending)
                    }
                    val withNew = profile.copy(entities = profile.entities + pc)
                    dispatchApply(withNew, change.id)
                }
                is ChangeItem.Removed -> profile.copy(
                    entities = profile.entities.filterNot { it.id == change.id },
                )
                is ChangeItem.ParamsChanged -> {
                    val withNewParams = profile.with(change.id, change.newComponent)
                    dispatchApply(withNewParams, change.id)
                }
            }
            store.save(profile)
        }
        return profile
    }

    private suspend fun dispatchApply(profile: Profile, id: String): Profile {
        val pc = profile.entities.firstOrNull { it.id == id } ?: return profile
        // Apply each domain-data component (the LifecycleState marker is not
        // applied — it IS the apply-state). Combine to the most-severe outcome and
        // record it as the entity's single LifecycleState. At MVP an entity holds
        // one data component, so this is behaviourally identical to the per-entity
        // dispatch it replaces.
        var worst: Outcome = Outcome.Ok
        for (c in pc.dataComponents()) {
            val provider: Provider<Component> = registry.resolve(c)
            val outcome = provider.apply(c, profile)
            if (outcome.severity() > worst.severity()) worst = outcome
        }
        return profile.setState(id, worst.toLifecycleState())
    }

    private suspend fun dispatchCheck(profile: Profile, id: String): Outcome {
        val pc = profile.entities.firstOrNull { it.id == id } ?: return Outcome.Unsupported
        var worst: Outcome = Outcome.Ok
        for (c in pc.dataComponents()) {
            val provider: Provider<Component> = registry.resolve(c)
            val outcome = provider.check(c, profile)
            if (outcome.severity() > worst.severity()) worst = outcome
        }
        return worst
    }

    /** The domain-data components — everything except the [LifecycleState] marker. */
    private fun com.launcher.preset.model.Entity.dataComponents(): List<Component> =
        components.filterNot { it is LifecycleState }

    private fun com.launcher.preset.model.Entity.isApplied(): Boolean =
        get<LifecycleState>() is LifecycleState.Applied

    private fun Outcome.severity(): Int = when (this) {
        is Outcome.Failed -> 5
        Outcome.NeedsUserConfirmation -> 4
        Outcome.NeedsApply -> 3
        Outcome.Unsupported -> 2
        Outcome.Ok -> 1
    }

    private fun Outcome.toLifecycleState(): LifecycleState = when (this) {
        Outcome.Ok, Outcome.NeedsApply -> LifecycleState.Applied
        is Outcome.Failed -> LifecycleState.Failed(reason)
        Outcome.Unsupported -> LifecycleState.Skipped
        // T127-016 (FR-014): the OS gives no read-back, so we record the honest
        // "cannot verify" rather than a fictional Applied. Only interactive paths
        // (Wizard / RunMode.Single) reach here; BootCheck skips such entities.
        Outcome.NeedsUserConfirmation -> LifecycleState.Unverifiable
    }
}
