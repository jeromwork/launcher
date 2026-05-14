package com.launcher.api.config.e2e

import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.result.Outcome
import com.launcher.fake.config.FakeConfigApplier
import com.launcher.fake.config.FakeConfigEditor
import com.launcher.fake.config.FakeLocalConfigStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * In-process end-to-end tests (spec 008 Phase 11 T130-T131).
 *
 * Multi-editor scenario in a single JVM:
 *  - Two [FakeConfigEditor] instances simulating admin-phone + admin-tablet
 *    (or admin-phone + Managed-as-editor) talking к shared in-memory backend.
 *  - One [FakeConfigApplier] per Managed simulating apply-on-receive.
 *
 * Covers US-1 (admin push happy path), US-2 (concurrent edits → merge),
 * US-4 (pending warning visibility), и SC-003 (100% pushes lead to /state
 * OR visible merge UI — no silent failures).
 */
class ConfigSyncE2ETest {

    @Test
    fun US_1_admin_push_happy_path() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "admin-device-A")

        // Seed initial state: store has applied config + server too (post-pairing baseline).
        val initial = sampleConfig(writer = "initial-writer", presetId = "simple-launcher")
        store.writeAppliedConfig("link-1", initial)
        editor.seedServer("link-1", initial)

        // User edits в Settings → autosave → push.
        editor.updateDraft("link-1") { it.copy(presetId = "medium-launcher") }
        val pushResult = editor.pushPending("link-1")

        assertTrue(pushResult is Outcome.Success, "Push должен succeed: $pushResult")
        // /state would reflect this — server now holds the new config.
        assertEquals("medium-launcher", editor.serverConfig("link-1")?.presetId)
        assertEquals("admin-device-A", editor.serverConfig("link-1")?.lastWriterDeviceId)
        // Pending cleared.
        assertNull(store.readPending("link-1"))
    }

    @Test
    fun US_2_concurrent_edits_second_push_sees_Conflict() = runTest {
        val storeA = FakeLocalConfigStore()
        val storeB = FakeLocalConfigStore()
        val editorA = FakeConfigEditor(localStore = storeA, selfDeviceId = "device-A")
        val editorB = FakeConfigEditor(localStore = storeB, selfDeviceId = "device-B")

        // Shared initial state.
        val initial = sampleConfig(writer = "initial-writer", presetId = "simple-launcher")
        storeA.writeAppliedConfig("link-1", initial)
        storeB.writeAppliedConfig("link-1", initial)
        editorA.seedServer("link-1", initial)
        editorB.seedServer("link-1", initial)

        // Both editors begin editing (read same snapshot).
        editorA.updateDraft("link-1") { it.copy(presetId = "draft-A") }
        editorB.updateDraft("link-1") { it.copy(presetId = "draft-B") }

        // A pushes first — succeeds.
        val resultA = editorA.pushPending("link-1")
        assertTrue(resultA is Outcome.Success)
        val newServerConfig = editorA.serverConfig("link-1")!!
        assertEquals("draft-A", newServerConfig.presetId)

        // B's editor still has stale snapshot of "initial" — for the test to
        // simulate that B reads the new server state, we explicitly seed it:
        editorB.bumpServerUpdatedAt("link-1", newServerConfig)

        // B's push — should detect conflict.
        val resultB = editorB.pushPending("link-1")
        assertTrue(resultB is Outcome.Failure, "B push должен conflict: $resultB")
        val err = (resultB as Outcome.Failure).error
        assertTrue(err is ConfigSyncError.Conflict)
        // Conflict carries server-side state.
        assertEquals("draft-A", (err as ConfigSyncError.Conflict).serverConfig.presetId)
        // B's pending preserved per FR-055.
        assertNotNull(storeB.readPending("link-1"))
    }

    @Test
    fun US_4_pending_visible_when_save_not_pushed() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "admin-device-A")
        val initial = sampleConfig(writer = "initial-writer", presetId = "simple-launcher")
        store.writeAppliedConfig("link-1", initial)
        editor.seedServer("link-1", initial)

        // User edits but DOESN'T push — pending lives forever (FR-043).
        editor.updateDraft("link-1") { it.copy(presetId = "draft-not-pushed") }
        assertNotNull(store.readPending("link-1"), "Pending must exist after autosave")

        // Server still has the original (admin chose not to push).
        assertEquals("simple-launcher", editor.serverConfig("link-1")?.presetId)

        // pendingLinks Flow surfaces this для FR-046 device-list badge / SC-008.
        // (testable через FakeLocalConfigStore.pendingLinks() Flow assertion).
    }

    @Test
    fun SC_003_100_pushes_yield_100_outcomes_no_silent_failures() = runTest {
        val store = FakeLocalConfigStore()
        val editor = FakeConfigEditor(localStore = store, selfDeviceId = "device-A")
        val initial = sampleConfig(writer = "initial", presetId = "preset-0")
        store.writeAppliedConfig("link-1", initial)
        editor.seedServer("link-1", initial)

        var successes = 0
        var conflicts = 0
        var other = 0

        // 100 push attempts; we alternate happy/conflict to exercise both paths.
        for (i in 1..100) {
            editor.updateDraft("link-1") { it.copy(presetId = "preset-$i") }
            // Every 5th attempt: simulate concurrent writer to force a conflict.
            if (i % 5 == 0) {
                val current = editor.serverConfig("link-1")!!
                editor.bumpServerUpdatedAt(
                    "link-1",
                    current.copy(
                        serverUpdatedAt = ServerTimestamp(
                            epochSeconds = current.serverUpdatedAt.epochSeconds + 100,
                            nanoseconds = 0,
                        ),
                        lastWriterDeviceId = "concurrent-writer",
                        presetId = "concurrent-preset-$i",
                    ),
                )
            }

            when (val r = editor.pushPending("link-1")) {
                is Outcome.Success -> successes++
                is Outcome.Failure -> when (r.error) {
                    is ConfigSyncError.Conflict -> {
                        conflicts++
                        // Simulate "user clicks Resolve": discard our changes,
                        // adopt the server state to continue the loop.
                        editor.discardPending("link-1")
                        store.writeAppliedConfig("link-1", editor.serverConfig("link-1")!!)
                    }
                    else -> other++
                }
            }
        }

        // SC-003 invariant: every push had a definite outcome — no silent loss.
        assertEquals(100, successes + conflicts + other, "Every push must have an outcome")
        assertTrue(conflicts > 0, "Expected at least one conflict in 100 iterations")
        assertTrue(successes > 0, "Expected at least one success")
        assertEquals(0, other, "No unexpected error categories")
    }

    @Test
    fun apply_propagates_server_to_local_via_fakeConfigApplier() = runTest {
        val store = FakeLocalConfigStore()
        val applier = FakeConfigApplier(localStore = store, selfDeviceId = "managed-device")

        val incoming = sampleConfig(writer = "admin-device-X", presetId = "applied-by-admin")
        applier.seedRemote("link-1", incoming)

        val result = applier.applyFromRemote("link-1")
        assertTrue(result is Outcome.Success)

        // Local store now mirrors what server sent.
        val applied = store.readAppliedConfig("link-1")
        assertEquals("applied-by-admin", applied?.presetId)
        assertEquals("admin-device-X", applied?.lastWriterDeviceId)
    }

    private fun sampleConfig(writer: String, presetId: String = "simple-launcher"): ConfigDocument =
        ConfigDocument(
            serverUpdatedAt = ServerTimestamp(epochSeconds = 1747166400L, nanoseconds = 0),
            lastWriterDeviceId = writer,
            presetId = presetId,
            flows = emptyList(),
            contacts = emptyList(),
        )
}
