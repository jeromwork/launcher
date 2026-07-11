package com.launcher.preset.port

import kotlin.jvm.JvmInline

@JvmInline
value class PairingId(val opaqueId: String)

/**
 * Resolves pairing target for identity-bound Components (Sos, MessengerTile handoff,
 * admin push). Preset stays identity-free (rule 9); Providers resolve at apply-time.
 * Real Android adapter deferred to TASK-67 pairing spec.
 */
interface PairingService {
    suspend fun currentAdmin(): PairingId?
}
