package com.launcher.api.auth

import com.launcher.api.setup.GmsAvailabilityPort
import com.launcher.api.setup.GmsStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame

class AuthAdapterSelectorTest {

    private class FakeGmsPort(private val status: GmsStatus) : GmsAvailabilityPort {
        override suspend fun status(): GmsStatus = status
    }

    private val realAdapter = FakeAuthAdapter(listOf(FakeAuthAdapter.DEFAULT_USER))

    @Test
    fun gmsAvailable_picksRealAdapter() = runTest {
        val selector = AuthAdapterSelector(
            gmsAvailabilityPort = FakeGmsPort(GmsStatus.Available),
            realAdapterFactory = { realAdapter },
        )
        assertSame(realAdapter, selector.pick())
    }

    @Test
    fun gmsMissingRecoverable_picksRealAdapter() = runTest {
        val selector = AuthAdapterSelector(
            gmsAvailabilityPort = FakeGmsPort(
                GmsStatus.MissingRecoverable("update needed", resolutionAvailable = true),
            ),
            realAdapterFactory = { realAdapter },
        )
        assertSame(realAdapter, selector.pick())
    }

    @Test
    fun gmsMissingFatal_picksNoSupportedAuthProvider() = runTest {
        val selector = AuthAdapterSelector(
            gmsAvailabilityPort = FakeGmsPort(GmsStatus.MissingFatal("Huawei")),
            realAdapterFactory = { error("should not be invoked when GMS fatally missing") },
        )
        assertSame(NoSupportedAuthProvider, selector.pick())
    }
}
