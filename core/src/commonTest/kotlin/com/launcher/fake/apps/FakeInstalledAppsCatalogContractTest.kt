package com.launcher.fake.apps

import com.launcher.api.apps.InstalledApp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec 009 contract test for [FakeInstalledAppsCatalog] (Phase 4).
 * Locks contract: own package filtered out; sorted alphabetical by label
 * (case-insensitive). Real adapter must obey the same contract.
 */
class FakeInstalledAppsCatalogContractTest {

    @Test
    fun own_package_filtered_out() = runTest {
        val catalog = FakeInstalledAppsCatalog(
            listOf(
                InstalledApp("com.launcher.app", "Лончер", null),
                InstalledApp("com.whatsapp", "WhatsApp", null),
            ),
        )
        val apps = catalog.listApps()
        assertEquals(1, apps.size)
        assertEquals("com.whatsapp", apps[0].packageName)
    }

    @Test
    fun sorted_by_label_case_insensitive() = runTest {
        val catalog = FakeInstalledAppsCatalog(
            listOf(
                InstalledApp("com.b", "Telegram", null),
                InstalledApp("com.a", "alarmy", null),
                InstalledApp("com.c", "WhatsApp", null),
            ),
        )
        val labels = catalog.listApps().map { it.label }
        assertEquals(listOf("alarmy", "Telegram", "WhatsApp"), labels)
    }
}
