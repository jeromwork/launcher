package com.launcher.core.capability

import androidx.test.core.app.ApplicationProvider
import com.launcher.api.action.ProviderAvailability
import com.launcher.api.action.ProviderId
import com.launcher.api.action.ProviderRegistry
import com.launcher.api.action.ProviderState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [AndroidCapabilityCollector] mapping logic per FR-001.
 *
 * Strategy: feed pre-built [ProviderState] lists через mock [ProviderRegistry];
 * verify mapped [Capability] shape (providerId, displayName, iconId, available).
 *
 * `versionCode` lookup через PackageManager — для тестов без installed packages
 * вернёт null (NameNotFoundException → null), что и есть expected behaviour
 * для не-installed providers.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidCapabilityCollectorTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun collectorWith(states: List<ProviderState>): AndroidCapabilityCollector {
        val registry = mockk<ProviderRegistry>()
        every { registry.snapshot() } returns states
        every { registry.updates } returns MutableSharedFlow()
        return AndroidCapabilityCollector(context, registry)
    }

    @Test
    fun snapshot_mapsAvailableState_toCapability() {
        val collector = collectorWith(listOf(
            ProviderState(
                providerId = ProviderId.WHATSAPP,
                availability = ProviderAvailability.Available,
                installedPackage = "com.whatsapp",
            ),
        ))
        val result = collector.snapshot()
        assertEquals(1, result.size)
        val cap = result.first()
        assertEquals(ProviderId.WHATSAPP, cap.providerId)
        assertEquals("WhatsApp", cap.displayName)
        assertEquals("bundled:whatsapp", cap.iconId)
        assertTrue(cap.available, "available state must map to capability.available=true")
    }

    @Test
    fun snapshot_mapsMissingState_toCapability_unavailable() {
        val collector = collectorWith(listOf(
            ProviderState(
                providerId = ProviderId.TELEGRAM,
                availability = ProviderAvailability.Missing(installHint = null),
                installedPackage = null,
            ),
        ))
        val cap = collector.snapshot().first()
        assertFalse(cap.available, "missing state must map to capability.available=false")
        assertEquals("bundled:telegram", cap.iconId)
        assertEquals("Telegram", cap.displayName)
    }

    @Test
    fun snapshot_versionCode_isNull_whenPackageNotInstalled() {
        // Robolectric default — no real packages installed → getPackageInfo throws,
        // collector swallows and returns null versionCode. This is the path для
        // available providers based on platform feature (PHONE/SMS/BROWSER), not
        // package detection.
        val collector = collectorWith(listOf(
            ProviderState(
                providerId = ProviderId.PHONE,
                availability = ProviderAvailability.Available,
                installedPackage = null,
            ),
        ))
        val cap = collector.snapshot().first()
        assertEquals(null, cap.versionCode)
    }

    @Test
    fun snapshot_versionCode_nullForUnknownPackage() {
        // installedPackage указан но реально не установлен в Robolectric env →
        // getPackageInfo throws NameNotFoundException → collector returns null.
        val collector = collectorWith(listOf(
            ProviderState(
                providerId = ProviderId.WHATSAPP,
                availability = ProviderAvailability.Available,
                installedPackage = "com.nonexistent.fake",
            ),
        ))
        val cap = collector.snapshot().first()
        assertEquals(null, cap.versionCode)
    }

    @Test
    fun snapshot_unknownProviderId_fallsBackToTitlecaseName() {
        val customProviderId = ProviderId.fromWire("smart_assistant")
        val collector = collectorWith(listOf(
            ProviderState(
                providerId = customProviderId,
                availability = ProviderAvailability.Available,
                installedPackage = null,
            ),
        ))
        val cap = collector.snapshot().first()
        // No DISPLAY_NAMES entry → fallback to titlecase of providerId.value.
        assertEquals("Smart_assistant", cap.displayName)
        assertEquals("bundled:smart_assistant", cap.iconId)
    }
}
