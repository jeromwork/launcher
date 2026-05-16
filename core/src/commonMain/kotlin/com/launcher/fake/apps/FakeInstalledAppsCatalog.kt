package com.launcher.fake.apps

import com.launcher.api.apps.InstalledApp
import com.launcher.api.apps.InstalledAppsCatalog

/**
 * In-memory fake for [InstalledAppsCatalog] (spec 009 Phase 4). Lets tests
 * inject a fixed app list; mirrors real adapter behaviour by sorting
 * case-insensitive by label and filtering out our own package
 * (`OWN_PACKAGE_NAME`).
 */
class FakeInstalledAppsCatalog(
    private val apps: List<InstalledApp> = emptyList(),
) : InstalledAppsCatalog {

    override suspend fun listApps(): List<InstalledApp> = apps
        .filterNot { it.packageName == OWN_PACKAGE_NAME }
        .sortedBy { it.label.lowercase() }

    companion object {
        const val OWN_PACKAGE_NAME = "com.launcher.app"
    }
}
