package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.admin.AdminEditorMode
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.config.LocalConfigStore
import com.launcher.api.config.PendingLocalChanges
import com.launcher.api.config.ServerTimestamp
import com.launcher.api.history.ConfigHistoryRepository
import com.launcher.api.history.ConfigSnapshotWithId
import com.launcher.api.history.RepositoryError
import com.launcher.api.result.Outcome
import com.launcher.api.config.ConfigSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Spec 009 Phase F2 — EditorComponent state-survival tests (FR-014a/b
 * Activity recreation).
 *
 * The test uses a controlled [LifecycleRegistry] to simulate Activity
 * destruction; afterwards a new EditorComponent on a fresh lifecycle
 * receives the same [ConfigEditor]/[LocalConfigStore] singletons и
 * reads the same persisted state — guaranteeing draft survival.
 *
 * Per memory `feedback_timeouts_30s`: the test uses
 * StandardTestDispatcher's advanceUntilIdle so no real-time waits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditorComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUpMain() = Dispatchers.setMain(testDispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    private val seedConfig = ConfigDocument(
        serverUpdatedAt = ServerTimestamp(epochSeconds = 100, nanoseconds = 0),
        lastWriterDeviceId = "test-self-uid",
        presetId = "simple-launcher",
        flows = emptyList(),
        contacts = emptyList(),
    )

    @Test
    fun publish_records_snapshot_then_pushes_then_housekeeps() = runTest(testDispatcher) {
        val store = FakeStore(applied = seedConfig)
        val editor = FakeEditor(pushOutcome = Outcome.Success(Unit))
        val history = RecordingHistory()

        val component = component(store, editor, history)
        advanceUntilIdle()

        component.publish()
        advanceUntilIdle()

        assertEquals(1, history.recorded.size)
        assertEquals("test-self-uid", history.recorded[0].recordedFromDeviceId)
        assertEquals(1, editor.pushCalls.size)
        assertEquals(1, history.housekeepCalls)
    }

    @Test
    fun publish_conflict_surfaces_merge_state() = runTest(testDispatcher) {
        val store = FakeStore(applied = seedConfig)
        val conflict = ConfigSyncError.Conflict(
            localDiff = com.launcher.api.config.ConfigDiff(),
            serverConfig = seedConfig.copy(presetId = "other"),
        )
        val editor = FakeEditor(pushOutcome = Outcome.Failure(conflict))
        val history = RecordingHistory()

        val component = component(store, editor, history)
        advanceUntilIdle()
        component.publish()
        advanceUntilIdle()

        val state = component.state.value
        assertNotNull("mergeConflict must be set on Conflict", state.mergeConflict)
        assertEquals(seedConfig.copy(presetId = "other"), state.mergeConflict?.serverConfig)
    }

    @Test
    fun draft_observed_from_pending_flow() = runTest(testDispatcher) {
        val pendingFlow = MutableStateFlow<ConfigDocument?>(null)
        val store = FakeStore(applied = seedConfig)
        val editor = FakeEditor(pendingFlow = pendingFlow)
        val history = RecordingHistory()

        val component = component(store, editor, history)
        advanceUntilIdle()

        val mutated = seedConfig.copy(presetId = "edited")
        pendingFlow.value = mutated
        advanceUntilIdle()

        assertEquals(mutated, component.state.value.draft)
    }

    @Test
    fun draft_survives_component_destroy_and_recreate() = runTest(testDispatcher) {
        // Persistent singletons — represent Koin singles surviving Activity
        // recreation.
        val pendingFlow = MutableStateFlow<ConfigDocument?>(null)
        val store = FakeStore(applied = seedConfig)
        val editor = FakeEditor(pendingFlow = pendingFlow).apply {
            appliedSource = { store.applied }
        }
        val history = RecordingHistory()

        val lifecycleA = LifecycleRegistry().apply { resume() }
        val componentA = EditorComponent(
            componentContext = DefaultComponentContext(lifecycleA),
            linkId = "L1",
            configEditor = editor,
            historyRepository = history,
            selfDeviceId = "test-self-uid",
            nowMillis = { 12345L },
            onBack = {},
            onHistoryClick = {},
        )
        advanceUntilIdle()
        // User edits — pending arrives.
        val edited = seedConfig.copy(presetId = "after-edit")
        pendingFlow.value = edited
        advanceUntilIdle()
        assertEquals(edited, componentA.state.value.draft)

        // Activity destroyed.
        lifecycleA.destroy()
        advanceUntilIdle()

        // New component starts on a fresh lifecycle (rotation, process re-
        // attach). Singletons persist; pendingDraft re-emits last value.
        val lifecycleB = LifecycleRegistry().apply { resume() }
        val componentB = EditorComponent(
            componentContext = DefaultComponentContext(lifecycleB),
            linkId = "L1",
            configEditor = editor,
            historyRepository = history,
            selfDeviceId = "test-self-uid",
            nowMillis = { 12345L },
            onBack = {},
            onHistoryClick = {},
        )
        advanceUntilIdle()

        assertEquals(
            "Recreated component must observe the persisted draft (FR-014a/b)",
            edited,
            componentB.state.value.draft,
        )
    }

    @Test
    fun toggle_mode_flips_view_edit() = runTest(testDispatcher) {
        val component = component(FakeStore(applied = seedConfig), FakeEditor(), RecordingHistory())
        advanceUntilIdle()
        assertEquals(AdminEditorMode.View, component.state.value.mode)
        component.toggleMode()
        assertEquals(AdminEditorMode.Edit, component.state.value.mode)
        component.toggleMode()
        assertEquals(AdminEditorMode.View, component.state.value.mode)
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun component(
        store: FakeStore,
        editor: FakeEditor,
        history: ConfigHistoryRepository,
    ): EditorComponent {
        // Bind the editor's applied-source to the test's store so
        // configEditor.appliedConfig(linkId) routes through it.
        editor.appliedSource = { store.applied }
        val lifecycle = LifecycleRegistry().apply { resume() }
        return EditorComponent(
            componentContext = DefaultComponentContext(lifecycle),
            linkId = "L1",
            configEditor = editor,
            historyRepository = history,
            selfDeviceId = "test-self-uid",
            nowMillis = { 12345L },
            onBack = {},
            onHistoryClick = {},
        )
    }

    // In-memory store/editor/history specialised for these tests. We can't
    // use the production fakes directly because they're tuned for richer
    // scenarios; these stripped-down doubles keep the test focused.

    private class FakeStore(
        val applied: ConfigDocument?,
    ) : LocalConfigStore {
        override suspend fun readAppliedConfig(linkId: String): ConfigDocument? = applied
        override suspend fun writeAppliedConfig(linkId: String, config: ConfigDocument) = Unit
        override suspend fun readPending(linkId: String): PendingLocalChanges? = null
        override suspend fun writePending(linkId: String, pending: PendingLocalChanges) = Unit
        override suspend fun clearPending(linkId: String) = Unit
        override fun pendingLinks(): Flow<Set<String>> = flowOf(emptySet())
    }

    private class FakeEditor(
        val pushOutcome: Outcome<Unit, ConfigSyncError> = Outcome.Success(Unit),
        val pendingFlow: MutableStateFlow<ConfigDocument?> = MutableStateFlow(null),
    ) : ConfigEditor {
        val pushCalls = mutableListOf<String>()
        var appliedSource: () -> ConfigDocument? = { null }
        override suspend fun updateDraft(linkId: String, mutator: (ConfigDocument) -> ConfigDocument) = Unit
        override fun pendingDraft(linkId: String): Flow<ConfigDocument?> = pendingFlow
        override suspend fun appliedConfig(linkId: String): ConfigDocument? = appliedSource()
        override suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError> {
            pushCalls.add(linkId); return pushOutcome
        }
        override suspend fun discardPending(linkId: String) = Unit
    }

    private class RecordingHistory : ConfigHistoryRepository {
        val recorded = mutableListOf<ConfigSnapshot>()
        var housekeepCalls = 0
        override suspend fun recordSnapshot(linkId: String, snapshot: ConfigSnapshot): Outcome<Unit, RepositoryError> {
            recorded.add(snapshot); return Outcome.Success(Unit)
        }
        override suspend fun readAll(linkId: String): Outcome<List<ConfigSnapshotWithId>, RepositoryError> =
            Outcome.Success(recorded.mapIndexed { i, s -> ConfigSnapshotWithId(autoId = "id-$i", snapshot = s) })
        override suspend fun housekeep(linkId: String, retentionCount: Int): Outcome<Unit, RepositoryError> {
            housekeepCalls++; return Outcome.Success(Unit)
        }
    }
}
