package com.launcher.adapters.crypto

import com.launcher.api.crypto.DeviceId
import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.DeviceIdentityRepository
import com.launcher.api.crypto.RecipientResolver

// Spec 011 RecipientResolver implementation for one-on-one pair.
// Возвращает identity peer'a (другого member'а пары). В 011 — 1 entry.
// Future spec 014 (groups) — другая реализация, FR-060 + spec.md C-8.
internal class PairRecipientResolver(
    private val repo: DeviceIdentityRepository,
    private val ownDeviceId: () -> DeviceId,
) : RecipientResolver {

    override suspend fun resolveRecipients(linkId: String): List<DeviceIdentity> {
        val own = ownDeviceId()
        return repo.listAll(linkId).filter { it.deviceId != own }
    }
}
