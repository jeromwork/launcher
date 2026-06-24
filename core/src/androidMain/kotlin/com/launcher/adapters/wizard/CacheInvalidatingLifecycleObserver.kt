package com.launcher.adapters.wizard

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Bulk-invalidates [SettingStatusCache] every time the host
 * [LifecycleOwner] hits `RESUMED`. Per data-model.md §4.2 / FR-022 —
 * users who flip a system setting in Android Settings expect the wizard
 * (or Settings checklist) to reflect the new value immediately on
 * return to the app.
 *
 * Registered against `WizardActivity` and any Settings screen that
 * surfaces pending-system-setting state.
 */
class CacheInvalidatingLifecycleObserver(
    private val cache: SettingStatusCache,
) : DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        cache.invalidateAll()
    }
}
