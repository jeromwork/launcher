package com.launcher.ui.paired

import com.launcher.api.identity.AdminIdentity
import com.launcher.api.link.Link
import com.launcher.fake.link.FakeLinkRegistry
import com.launcher.fake.paired.InMemoryLocalLinkRevocationStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec 010 T091 — `UnlinkLocalFirstTest` covering the FR-032 + FR-032a paths
 * that are commonTest-reachable (paths (a) online success, (b) offline UX
 * guarantee, (c) idempotent re-enqueue). Path (d) WorkManager backoff is
 * inherently Android-only and verified via the smoke pass T093 on a
 * physical device (deferred per memory `feedback_critical_mentor_stance.md`).
 */
class PairedDevicesPresenterTest {

    private val testLink = Link(
        linkId = "link-7",
        adminId = AdminIdentity("admin-uid-abc12345"),
        managedDeviceId = "managed-device-1",
        managedDeviceFirebaseUid = "managed-uid-1",
        createdAt = 1747200000000L,
    )

    @Test
    fun confirmUnlink_marks_link_locally_revoked_and_enqueues_cleanup() = runTest {
        val registry = FakeLinkRegistry(initial = testLink)
        val store = InMemoryLocalLinkRevocationStore()
        val enqueued = mutableListOf<String>()
        val presenter = PairedDevicesPresenter(
            linkRegistry = registry,
            revocationStore = store,
            enqueueCleanup = { enqueued += it },
        )

        // Before unlink: «Кто помогает мне» shows the link.
        val before = presenter.items().first()
        assertEquals(1, before.helpsMe.size)
        assertEquals("link-7", before.helpsMe.single().linkId)

        // FR-032 (a) + (b): mark locally + queue cleanup.
        presenter.confirmUnlink("link-7")

        assertTrue(store.isRevoked("link-7").first(), "store flag flipped")
        assertEquals(listOf("link-7"), enqueued, "worker enqueue fired exactly once")

        // FR-032 (c) UX guarantee: «Кто помогает мне» list now empty even
        // though LinkRegistry still holds the link (server cleanup queued).
        val after = presenter.items().first()
        assertTrue(after.helpsMe.isEmpty(), "locally-revoked link disappears immediately")
    }

    @Test
    fun confirmUnlink_idempotent_on_double_tap() = runTest {
        val registry = FakeLinkRegistry(initial = testLink)
        val store = InMemoryLocalLinkRevocationStore()
        val enqueued = mutableListOf<String>()
        val presenter = PairedDevicesPresenter(
            linkRegistry = registry,
            revocationStore = store,
            enqueueCleanup = { enqueued += it },
        )

        presenter.confirmUnlink("link-7")
        presenter.confirmUnlink("link-7")  // second tap by hesitant user

        assertEquals(setOf("link-7"), store.snapshot(), "store set has one entry")
        assertEquals(
            listOf("link-7", "link-7"),
            enqueued,
            "enqueueCleanup is called twice (WorkManager dedupes via KEEP policy)",
        )
    }

    @Test
    fun items_displayName_falls_back_to_short_uid_until_admin_metadata_lands() = runTest {
        val registry = FakeLinkRegistry(initial = testLink)
        val store = InMemoryLocalLinkRevocationStore()
        val presenter = PairedDevicesPresenter(
            linkRegistry = registry,
            revocationStore = store,
            enqueueCleanup = {},
        )
        val item = presenter.items().first().helpsMe.single()
        assertEquals("admin-ui", item.displayName, "first 8 chars of admin uid")
    }

    @Test
    fun items_empty_when_no_current_link() = runTest {
        val registry = FakeLinkRegistry(initial = null)
        val store = InMemoryLocalLinkRevocationStore()
        val presenter = PairedDevicesPresenter(
            linkRegistry = registry,
            revocationStore = store,
            enqueueCleanup = {},
        )
        val state = presenter.items().first()
        assertTrue(state.helpsMe.isEmpty())
        assertTrue(state.iHelp.isEmpty())
    }
}
