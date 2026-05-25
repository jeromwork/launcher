package com.launcher.fake.crypto

import com.launcher.api.crypto.DeviceIdentity
import com.launcher.api.crypto.RecipientResolver

class FakeRecipientResolver : RecipientResolver {
    private val byLink = mutableMapOf<String, List<DeviceIdentity>>()

    fun setRecipients(linkId: String, recipients: List<DeviceIdentity>) {
        byLink[linkId] = recipients
    }

    override suspend fun resolveRecipients(linkId: String): List<DeviceIdentity> =
        byLink[linkId] ?: emptyList()
}
