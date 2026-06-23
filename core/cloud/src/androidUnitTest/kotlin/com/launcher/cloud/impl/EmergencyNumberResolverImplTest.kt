package com.launcher.cloud.impl

import android.os.Build
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EmergencyNumberResolverImplTest {

    private fun setSdk(sdkInt: Int) {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdkInt)
    }

    @Test
    @Config(sdk = [28])
    fun `RU locale on API less than 29 returns 102`() = runTest {
        setSdk(28)
        val tm = mockk<TelephonyManager>(relaxed = true)
        val impl = EmergencyNumberResolverImpl(tm) { Locale("ru", "RU") }
        assertEquals("102", impl.getEmergencyNumber())
    }

    @Test
    @Config(sdk = [28])
    fun `US locale on API less than 29 returns 911`() = runTest {
        setSdk(28)
        val tm = mockk<TelephonyManager>(relaxed = true)
        val impl = EmergencyNumberResolverImpl(tm) { Locale("en", "US") }
        assertEquals("911", impl.getEmergencyNumber())
    }

    @Test
    @Config(sdk = [28])
    fun `DE locale on API less than 29 returns 112`() = runTest {
        setSdk(28)
        val tm = mockk<TelephonyManager>(relaxed = true)
        val impl = EmergencyNumberResolverImpl(tm) { Locale("de", "DE") }
        assertEquals("112", impl.getEmergencyNumber())
    }

    @Test
    @Config(sdk = [29])
    fun `API 29 plus with empty system list falls back to locale`() = runTest {
        setSdk(29)
        val tm = mockk<TelephonyManager>(relaxed = true)
        every { tm.emergencyNumberList } returns emptyMap()
        val impl = EmergencyNumberResolverImpl(tm) { Locale("ru", "RU") }
        assertEquals("102", impl.getEmergencyNumber())
    }
}
