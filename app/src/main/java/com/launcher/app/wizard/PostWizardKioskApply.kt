package com.launcher.app.wizard

import com.launcher.preset.ecs.get
import com.launcher.preset.model.Component
import com.launcher.preset.model.LifecycleState
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
        for (pc in profile.entities) {
            // Free bag: the kiosk data component is whichever of the two the entity
            // carries (get<T>() reads it directly — no single `pc.component`).
            val kioskComponent: Component =
                pc.get<Component.StatusBarPolicy>() ?: pc.get<Component.LauncherRole>() ?: continue

            @Suppress("UNCHECKED_CAST")
            val provider = registry.resolve(kioskComponent) as Provider<Component>
            current = when (val outcome = provider.apply(kioskComponent, current)) {
                Outcome.Ok, Outcome.NeedsApply ->
                    current.setState(pc.id, LifecycleState.Applied)
                is Outcome.Failed ->
                    current.setState(pc.id, LifecycleState.Failed(outcome.reason))
                Outcome.Unsupported ->
                    current.setState(pc.id, LifecycleState.Skipped)
                // T127-016 (FR-014): status-bar hiding has no read-back on Android —
                // the user was sent to system settings and confirmed by hand. Record
                // the honest "cannot verify" instead of a fictional Applied.
                Outcome.NeedsUserConfirmation ->
                    current.setState(pc.id, LifecycleState.Unverifiable)
            }
        }
        store.save(current)
        return current
    }
}
