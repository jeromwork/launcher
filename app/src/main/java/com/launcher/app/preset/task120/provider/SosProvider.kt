package com.launcher.app.preset.task120.provider

import com.launcher.app.preset.task120.facade.HomeScreenFacade
import com.launcher.preset.model.Component
import com.launcher.preset.model.FailReason
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.port.PairingService
import com.launcher.preset.port.Provider

class SosProvider(
    private val home: HomeScreenFacade,
    private val pairing: PairingService,
) : Provider<Component.Sos> {

    override suspend fun check(component: Component.Sos, profile: Profile): Outcome =
        if (home.hasSosTile()) Outcome.Ok else Outcome.NeedsApply

    override suspend fun apply(component: Component.Sos, profile: Profile): Outcome {
        val admin = pairing.currentAdmin()
        if (admin == null) return Outcome.Failed(FailReason.PairingNotEstablished)
        home.addSosTile(pinProtected = true)
        return Outcome.Ok
    }
}
