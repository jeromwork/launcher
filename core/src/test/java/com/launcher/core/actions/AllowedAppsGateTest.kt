package com.launcher.core.actions

import com.launcher.api.AllowedAppsPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedAppsGateTest {

    @Test
    fun allowsWhitelistedPackageAndBlocksOthers() {
        val gate = AllowedAppsGate(
            policyProvider = {
                AllowedAppsPolicy(
                    allowedPackages = setOf("com.whatsapp"),
                    alwaysAllowedPackages = setOf("com.launcher.app"),
                )
            },
        )

        assertTrue(gate.isAllowed("com.whatsapp"))
        assertTrue(gate.isAllowed("com.launcher.app"))
        assertFalse(gate.isAllowed("com.android.settings"))
    }
}

