package com.launcher.adapters.crypto

import family.pairing.api.DeviceId
import family.pairing.api.DeviceIdentity
import family.pairing.api.DeviceIdentityRepository
import family.pairing.api.RecipientResolver

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
