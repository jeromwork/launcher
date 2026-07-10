package com.launcher.app.preset.task120.provider

import com.launcher.app.preset.task120.facade.HomeScreenFacade
import com.launcher.app.preset.task120.facade.PackageManagerFacade
import com.launcher.app.preset.task120.facade.StoreIntentFacade
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.Provider

class AppTileProvider(
    private val pm: PackageManagerFacade,
    private val home: HomeScreenFacade,
    private val store: StoreIntentFacade,
) : Provider<Component.AppTile> {

    override suspend fun check(component: Component.AppTile, profile: Profile): Outcome =
        if (home.hasTile(component.packageName)) Outcome.Ok else Outcome.NeedsApply

    override suspend fun apply(component: Component.AppTile, profile: Profile): Outcome {
        if (!pm.isInstalled(component.packageName)) {
            val launched = store.launchStoreForPackage(component.packageName)
            if (!launched) return Outcome.Failed(FailReason.NetworkUnavailable)
        }
        home.addTile(
            packageName = component.packageName,
            labelKey = component.labelKey,
            iconKey = component.iconKey,
            pinProtected = component.pinProtected,
        )
        return Outcome.Ok
    }
}
