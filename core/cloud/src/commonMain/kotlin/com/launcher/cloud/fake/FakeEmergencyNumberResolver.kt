package com.launcher.cloud.fake

import com.launcher.cloud.api.EmergencyNumberResolver

class FakeEmergencyNumberResolver(private val number: String) : EmergencyNumberResolver {
    override suspend fun getEmergencyNumber(): String = number
}
