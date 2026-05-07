package com.launcher.core.actions

import android.content.Context
import com.launcher.api.ActionRequest
import com.launcher.api.BlockReason
import com.launcher.api.DispatchResult
import com.launcher.core.catalog.AppIndex
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatcherTest {

    @Test
    fun openApplicationBlankKeyIsBlockedInvalidRequest() {
        val context = mockk<Context>(relaxed = true)
        val index = mockk<AppIndex>(relaxed = true)
        val dispatcher = ActionDispatcher(context, index)

        val r = dispatcher.dispatch(ActionRequest.OpenApplication("  \t  "))

        assertEquals(DispatchResult.BlockedByPolicy(BlockReason.INVALID_REQUEST), r)
        verify(exactly = 0) { index.findEntry(any()) }
    }

    @Test
    fun openApplicationMissingFromCatalogIsNotInCatalog() {
        val context = mockk<Context>(relaxed = true)
        val index = mockk<AppIndex>()
        every { index.findEntry("com.unknown") } returns null
        val dispatcher = ActionDispatcher(context, index)

        val r = dispatcher.dispatch(ActionRequest.OpenApplication("com.unknown"))

        assertTrue(r is DispatchResult.BlockedByPolicy)
        assertEquals(BlockReason.NOT_IN_CATALOG, (r as DispatchResult.BlockedByPolicy).reason)
    }
}
