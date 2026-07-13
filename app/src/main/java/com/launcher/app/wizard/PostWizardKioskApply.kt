package com.launcher.app.wizard

import com.launcher.preset.model.Component
import com.launcher.preset.model.ComponentStatus
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.port.Provider
import com.launcher.preset.port.ProviderRegistry

/**
 * TASK-126 T056 — kiosk components ([Component.StatusBarPolicy] +
 * [Component.LauncherRole]) are intentionally NOT applied by
 * [com.launcher.preset.engine.ReconcileEngine.runWizard]: mid-wizard, hiding
 * the status bar or firing the ROLE_HOME chooser mid-flow would disorient a
 * senior user. They are applied here instead, exactly once, on the transition
 * from wizard-completion to home screen.
 *
 * The apply is best-effort: any [Outcome.Failed] is recorded on the
 * [Profile] but does NOT block home navigation. A subsequent BootCheck run
 * will re-attempt the kiosk step via the standard `critical=true` path.
 */
class PostWizardKioskApply(
    private val registry: ProviderRegistry,
    private val store: ProfileStore,
) {

    suspend fun applyKiosk(profile: Profile): Profile {
        var current = profile
        for (pc in profile.components) {
            val comp = pc.component
            val isKiosk = comp is Component.StatusBarPolicy || comp is Component.LauncherRole
            if (!isKiosk) continue

            @Suppress("UNCHECKED_CAST")
            val provider = registry.resolve(comp) as Provider<Component>
            current = when (provider.apply(comp, current)) {
                Outcome.Ok, Outcome.NeedsApply ->
                    current.mark(pc.id, ComponentStatus.Applied)
                is Outcome.Failed ->
                    current.mark(pc.id, ComponentStatus.Failed)
                Outcome.Unsupported ->
                    current.mark(pc.id, ComponentStatus.Skipped)
            }
        }
        store.save(current)
        return current
    }
}
