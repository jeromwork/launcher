package com.launcher.adapters.crypto

import cryptokit.pairing.api.DeviceId
import cryptokit.pairing.api.DeviceIdentity
import cryptokit.pairing.api.DeviceIdentityRepository
import cryptokit.pairing.api.RecipientResolver

// Spec 011 RecipientResolver implementation for one-on-one pair.
// Возвращает identity peer'a (другого member'а пары). В 011 — 1 entry.
// Future spec 014 (groups) — другая реализация, FR-060 + spec.md C-8.
class PairRecipientResolver(
    private val repo: DeviceIdentityRepository,
    private val ownDeviceId: () -> DeviceId,
) : RecipientResolver {

    override suspend fun resolveRecipients(linkId: String): List<DeviceIdentity> {
        val own = ownDeviceId()
        return repo.listAll(linkId).filter { it.deviceId != own }
    }
}
