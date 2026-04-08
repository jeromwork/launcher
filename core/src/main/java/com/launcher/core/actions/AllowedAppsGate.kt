package com.launcher.core.actions

import com.launcher.api.AllowedAppsPolicy

class AllowedAppsGate(
    private val policyProvider: () -> AllowedAppsPolicy,
) {
    fun isAllowed(packageName: String): Boolean {
        val policy = policyProvider()
        if (policy.allowedPackages.contains("*")) {
            return true
        }
        return policy.isPackageAllowed(packageName)
    }
}

