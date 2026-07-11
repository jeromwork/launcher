package com.launcher.app.preset.task120.adapter

import com.launcher.preset.port.PairingId
import com.launcher.preset.port.PairingService

/**
 * MVP stub — always returns null. Real adapter reads from the pairing store
 * introduced by TASK-67 pairing spec. Sos.apply() will return
 * FailReason.PairingNotEstablished until then; Wizard offers a nested
 * pairing step (also deferred to TASK-67).
 */
class NoopPairingService : PairingService {
    override suspend fun currentAdmin(): PairingId? = null
}
