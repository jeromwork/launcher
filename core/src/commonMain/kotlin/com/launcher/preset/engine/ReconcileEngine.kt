package com.launcher.preset.engine

import com.launcher.preset.model.ChangeItem
import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.ProfileComponent
import com.launcher.preset.model.RunMode
import com.launcher.preset.model.WizardBehavior
import com.launcher.preset.port.InteractionSink
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import com.launcher.preset.port.ProviderRegistry

/**
 * Single reconcile engine covering all 4 RunMode branches (FR-010).
 *
 * NB: engine MUST NOT `when` on concrete Component subtypes (fitness #2). All
 * subtype-specific logic goes through the ProviderRegistry indirection.
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
        for (pc in initial.components.filter { it.status != ComponentStatus.Applied }) {
            profile = when (pc.wizardBehavior) {
                WizardBehavior.Interactive -> {
                    val answered = sink?.askUser(pc)
                    if (answered == null) {
                        profile.mark(pc.id, ComponentStatus.Skipped)
                    } else {
                        val replaced = profile.replaceComponent(pc.id, answered)
                        dispatchApply(replaced, pc.id)
                    }
                }
                WizardBehavior.AutoApply -> dispatchApply(profile, pc.id)
                WizardBehavior.InitialDefault -> profile.mark(pc.id, ComponentStatus.Applied)
            }
            store.save(profile)
        }
        return profile
    }

    private suspend fun runBootCheck(initial: Profile): Profile {
        var profile = initial
        for (pc in initial.components.filter { it.critical }) {
            val checkResult = dispatchCheck(profile, pc.id)
            if (checkResult is Outcome.NeedsApply) {
                profile = dispatchApply(profile, pc.id)
            } else if (checkResult is Outcome.Failed) {
                profile = profile.mark(pc.id, ComponentStatus.Failed)
            }
            store.save(profile)
        }
        return profile
    }

    private suspend fun runSingle(initial: Profile, targetId: String): Profile {
        val pc = initial.components.firstOrNull { it.id == targetId }
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
                    val pc = ProfileComponent(
                        id = change.id,
                        component = change.component,
                        wizardBehavior = WizardBehavior.AutoApply,
                        critical = false,
                        status = ComponentStatus.Pending,
                    )
                    val withNew = profile.copy(components = profile.components + pc)
                    dispatchApply(withNew, change.id)
                }
                is ChangeItem.Removed -> profile.copy(
                    components = profile.components.filterNot { it.id == change.id }
                )
                is ChangeItem.ParamsChanged -> {
                    val withNewParams = profile.replaceComponent(change.id, change.newComponent)
                    dispatchApply(withNewParams, change.id)
                }
            }
            store.save(profile)
        }
        return profile
    }

    private suspend fun dispatchApply(profile: Profile, id: String): Profile {
        val pc = profile.components.firstOrNull { it.id == id } ?: return profile
        val provider: Provider<Component> = registry.resolve(pc.component)
        return when (val outcome = provider.apply(pc.component, profile)) {
            Outcome.Ok -> profile.mark(id, ComponentStatus.Applied)
            Outcome.NeedsApply -> profile.mark(id, ComponentStatus.Applied)
            is Outcome.Failed -> profile.mark(id, ComponentStatus.Failed)
            Outcome.Unsupported -> profile.mark(id, ComponentStatus.Skipped)
        }
    }

    private suspend fun dispatchCheck(profile: Profile, id: String): Outcome {
        val pc = profile.components.firstOrNull { it.id == id } ?: return Outcome.Unsupported
        val provider: Provider<Component> = registry.resolve(pc.component)
        return provider.check(pc.component, profile)
    }
}
