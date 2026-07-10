package com.launcher.preset.port

import com.launcher.preset.model.CapabilityFlag

interface CapabilityQuery {
    suspend fun isActive(flag: CapabilityFlag): Boolean
    suspend fun markActive(flag: CapabilityFlag, evidence: Evidence)
    suspend fun markInactive(flag: CapabilityFlag)

    sealed class Evidence {
        data class Token(val value: String, val expiresAt: Long?) : Evidence()
        data class Hash(val sha256: String) : Evidence()
        object Marker : Evidence()
    }
}
