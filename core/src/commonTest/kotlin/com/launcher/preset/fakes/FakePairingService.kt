package com.launcher.preset.fakes

import com.launcher.preset.port.PairingId
import com.launcher.preset.port.PairingService

class FakePairingService(private val current: PairingId? = null) : PairingService {
    override suspend fun currentAdmin(): PairingId? = current
}
