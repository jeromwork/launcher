package com.launcher.ui.wizard

import com.launcher.api.wizard.fakes.FakeClock
import com.launcher.api.wizard.fakes.FakeStringResolver
import com.launcher.api.wizard.fakes.InMemoryDismissedHintsStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TutorialHintManagerTest {

    @Test
    fun dismissPersistsAcrossInstances() = runTest {
        val store = InMemoryDismissedHintsStore()
        val resolver = FakeStringResolver()
        val clock = FakeClock()
        val first = TutorialHintManager(store, resolver, clock)
        assertFalse(first.isDismissed("first-tile-hint"))
        first.markDismissed("first-tile-hint")
        assertTrue(first.isDismissed("first-tile-hint"))
        val second = TutorialHintManager(store, resolver, clock)
        assertTrue(second.isDismissed("first-tile-hint"), "dismissal must persist across manager instances")
    }

    @Test
    fun differentHintIdsTrackedIndependently() = runTest {
        val store = InMemoryDismissedHintsStore()
        val mgr = TutorialHintManager(store, FakeStringResolver(), FakeClock())
        mgr.markDismissed("hint-a")
        assertTrue(mgr.isDismissed("hint-a"))
        assertFalse(mgr.isDismissed("hint-b"))
    }

    @Test
    fun reset_undoesDismissal() = runTest {
        val store = InMemoryDismissedHintsStore()
        val mgr = TutorialHintManager(store, FakeStringResolver(), FakeClock())
        mgr.markDismissed("hint-a")
        assertTrue(mgr.isDismissed("hint-a"))
        mgr.reset("hint-a")
        assertFalse(mgr.isDismissed("hint-a"))
    }
}
