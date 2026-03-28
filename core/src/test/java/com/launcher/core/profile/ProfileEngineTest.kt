package com.launcher.core.profile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.DegradationReason
import com.launcher.core.events.EventRouter
import com.launcher.core.modules.ModuleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileEngineTest {

    @Test
    fun corruptAssetFallsBackToBundledDefaultWithInvalidProfileReason() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        val engine = ProfileEngine(
            app,
            ModuleRegistry(emptyList()),
            EventRouter(scope),
            assetPath = "corrupt_profile.json",
        )
        engine.loadFromAssets()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val eff = engine.effectiveProfile.value
        assertTrue(eff.degradation.reasonCodes.contains(DegradationReason.INVALID_PROFILE_FALLBACK))
        assertEquals("default", eff.snapshot.id)
    }

    @Test
    fun missingAssetFallsBackWithInvalidProfileReason() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Main.immediate)
        val engine = ProfileEngine(
            app,
            ModuleRegistry(emptyList()),
            EventRouter(scope),
            assetPath = "does_not_exist.json",
        )
        engine.loadFromAssets()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val eff = engine.effectiveProfile.value
        assertTrue(eff.degradation.reasonCodes.contains(DegradationReason.INVALID_PROFILE_FALLBACK))
        assertEquals("default", eff.snapshot.id)
    }
}
