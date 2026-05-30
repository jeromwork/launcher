package com.launcher.ui.edit

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigEditor
import com.launcher.api.config.ConfigSyncError
import com.launcher.api.edit.EditError
import com.launcher.api.edit.EditUiProfile
import com.launcher.api.edit.PickerType
import com.launcher.api.edit.TargetIdentity
import com.launcher.api.result.Outcome
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EditModeComponent] state transitions per spec 014:
 *  - FR-005/FR-006/FR-009 — entry via target preset.
 *  - FR-008/FR-008b — profile derivation + custom preset refuse.
 *  - FR-016/FR-017 + Q7 — profile-asymmetric conflict handling.
 *  - FR-018/FR-019 — picker tab filtering by preset.
 *  - FR-020a — empty-state «+» tile bypasses edit mode.
 *
 * Pattern скопирован из [com.launcher.ui.admin.navigation.EditorComponentTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditModeComponentTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUpMain() = Dispatchers.setMain(testDispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun newComponent(editor: ConfigEditor = FakeEditor()): EditModeComponent {
        val lifecycle = LifecycleRegistry().apply { resume() }
        return EditModeComponent(
            componentContext = DefaultComponentContext(lifecycle),
            configEditor = editor,
        )
    }

    private val workspaceTarget = TargetIdentity.forSelf("workspace")
    private val simpleLauncherTarget = TargetIdentity.forSelf("simple-launcher")
    private val customTarget = TargetIdentity.forSelf("custom-preset-via-configurator")
    private val remoteWorkspaceTarget =
        TargetIdentity.forRemote(linkId = "abc-link", presetId = "workspace")

    // ─── enterEditMode / exitEditMode ─────────────────────────────────────

    @Test
    fun enterEditMode_with_workspace_target_yields_AdminProfile_active() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(workspaceTarget)

        val state = component.state.value
        assertNotNull(state.editMode)
        assertTrue(state.editMode!!.active)
        assertEquals(EditUiProfile.AdminProfile, state.editMode!!.profile)
        assertEquals(workspaceTarget, state.editMode!!.target)
    }

    @Test
    fun enterEditMode_with_simpleLauncher_target_yields_SeniorProfile_active() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(simpleLauncherTarget)

        val state = component.state.value
        assertEquals(EditUiProfile.SeniorProfile, state.editMode!!.profile)
        assertTrue(state.editMode!!.active)
    }

    @Test
    fun enterEditMode_with_custom_preset_surfaces_profile_selection_error() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(customTarget)

        val state = component.state.value
        assertNull("editMode must not be set on custom preset", state.editMode)
        assertEquals(
            EditError.ProfileSelectionRequiresCapabilityRegistry,
            state.profileSelectionError,
        )
    }

    @Test
    fun exitEditMode_clears_state_and_invokes_onExit() = runTest(testDispatcher) {
        var onExitCalled = false
        val lifecycle = LifecycleRegistry().apply { resume() }
        val component = EditModeComponent(
            componentContext = DefaultComponentContext(lifecycle),
            configEditor = FakeEditor(),
            onExit = { onExitCalled = true },
        )
        component.enterEditMode(workspaceTarget)
        component.exitEditMode()

        assertNull(component.state.value.editMode)
        assertTrue(onExitCalled)
    }

    // ─── visiblePickerTabs — FR-018 / FR-019 ──────────────────────────────

    @Test
    fun visiblePickerTabs_workspace_shows_all_5_tabs() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(workspaceTarget)

        assertEquals(PickerType.entries, component.visiblePickerTabs())
    }

    @Test
    fun visiblePickerTabs_simpleLauncher_shows_only_3_tabs() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(simpleLauncherTarget)

        assertEquals(
            listOf(PickerType.Application, PickerType.Contact, PickerType.Document),
            component.visiblePickerTabs(),
        )
    }

    @Test
    fun visiblePickerTabs_no_edit_mode_returns_empty_list() = runTest(testDispatcher) {
        val component = newComponent()
        assertEquals(emptyList<PickerType>(), component.visiblePickerTabs())
    }

    // ─── Picker visibility ────────────────────────────────────────────────

    @Test
    fun openPicker_only_works_in_active_edit_mode() = runTest(testDispatcher) {
        val component = newComponent()
        component.openPicker()
        assertFalse("picker must not open без edit mode", component.state.value.pickerVisible)

        component.enterEditMode(workspaceTarget)
        component.openPicker()
        assertTrue("picker should open in edit mode", component.state.value.pickerVisible)
    }

    @Test
    fun openPickerForEmptyState_opens_picker_without_setting_active_edit() = runTest(testDispatcher) {
        // Q6 / FR-020a — empty state «+» bypasses edit mode entry.
        val component = newComponent()
        component.openPickerForEmptyState(workspaceTarget)

        val state = component.state.value
        assertTrue("picker must be visible", state.pickerVisible)
        assertTrue("emptyStatePickerBypass flag must be set", state.emptyStatePickerBypass)
        assertFalse("editMode.active must be FALSE per Q6", state.editMode!!.active)
    }

    @Test
    fun dismissPicker_clears_picker_visibility() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(workspaceTarget)
        component.openPicker()
        component.dismissPicker()

        assertFalse(component.state.value.pickerVisible)
    }

    // ─── Conflict handling per profile (Q7 / FR-016 / FR-017) ─────────────

    @Test
    fun admin_conflict_surfaces_conflictPending_flag() = runTest(testDispatcher) {
        val editor = FakeEditor(
            pushOutcome = Outcome.Failure(
                ConfigSyncError.Conflict(
                    localDiff = com.launcher.api.config.ConfigDiff(),
                    serverConfig = seedConfig,
                ),
            ),
        )
        val component = newComponent(editor)
        component.enterEditMode(remoteWorkspaceTarget) // workspace → AdminProfile
        component.pushChanges()
        advanceUntilIdle()

        assertTrue(
            "admin profile must show conflict UI",
            component.state.value.conflictPending,
        )
    }

    @Test
    fun senior_conflict_is_silently_retried_no_conflict_UI() = runTest(testDispatcher) {
        // Senior profile: first push returns Conflict, but component handles
        // it silently — second push succeeds. No conflictPending flag.
        val editor = SequentialPushEditor(
            outcomes = listOf(
                Outcome.Failure(
                    ConfigSyncError.Conflict(
                        localDiff = com.launcher.api.config.ConfigDiff(),
                        serverConfig = seedConfig,
                    ),
                ),
                Outcome.Success(Unit),
            ),
        )
        val component = newComponent(editor)
        component.enterEditMode(simpleLauncherTarget) // simple-launcher → SeniorProfile
        component.pushChanges()
        advanceUntilIdle()

        assertFalse(
            "senior profile must NEVER show conflict UI per Q7",
            component.state.value.conflictPending,
        )
        assertEquals(
            "senior must push twice (conflict → silent retry)",
            2, editor.pushCount,
        )
    }

    @Test
    fun overwriteRemote_no_op_when_profile_is_senior() = runTest(testDispatcher) {
        // FR-017: «Перезаписать» доступно только в admin profile. Senior call
        // must be silently rejected (defense-in-depth — UI must never expose
        // overwrite button for senior, but model layer guards too).
        val editor = FakeEditor()
        val component = newComponent(editor)
        component.enterEditMode(simpleLauncherTarget)
        component.overwriteRemote()
        advanceUntilIdle()

        assertEquals("senior overwrite must be no-op", 0, editor.pushCalls.size)
    }

    @Test
    fun overwriteRemote_pushes_when_profile_is_admin() = runTest(testDispatcher) {
        val editor = FakeEditor(pushOutcome = Outcome.Success(Unit))
        val component = newComponent(editor)
        component.enterEditMode(workspaceTarget)
        // Trigger conflict path первым, then admin clicks «Перезаписать».
        component.overwriteRemote()
        advanceUntilIdle()

        assertEquals(1, editor.pushCalls.size)
        assertNull("editMode cleared after overwrite", component.state.value.editMode)
    }

    @Test
    fun dismissConflict_clears_flag_without_pushing() = runTest(testDispatcher) {
        val component = newComponent()
        component.enterEditMode(workspaceTarget)
        // Manually set conflictPending = true to simulate.
        // (Component design: only handleConflict sets it; for this unit test
        // we reach the same state through pushChanges + conflict.)
        val editor = FakeEditor(
            pushOutcome = Outcome.Failure(
                ConfigSyncError.Conflict(
                    localDiff = com.launcher.api.config.ConfigDiff(),
                    serverConfig = seedConfig,
                ),
            ),
        )
        val c = newComponent(editor)
        c.enterEditMode(workspaceTarget)
        c.pushChanges()
        advanceUntilIdle()
        assertTrue(c.state.value.conflictPending)

        c.dismissConflict()
        assertFalse(c.state.value.conflictPending)
    }

    // ─── Test doubles ─────────────────────────────────────────────────────

    private val seedConfig = ConfigDocument(
        serverUpdatedAt = com.launcher.api.config.ServerTimestamp(epochSeconds = 100, nanoseconds = 0),
        lastWriterDeviceId = "test-self-uid",
        presetId = "workspace",
        flows = emptyList(),
        contacts = emptyList(),
    )

    private class FakeEditor(
        val pushOutcome: Outcome<Unit, ConfigSyncError> = Outcome.Success(Unit),
        val pendingFlow: MutableStateFlow<ConfigDocument?> = MutableStateFlow(null),
    ) : ConfigEditor {
        val pushCalls = mutableListOf<String>()
        var appliedSource: () -> ConfigDocument? = { null }
        override suspend fun updateDraft(linkId: String, mutator: (ConfigDocument) -> ConfigDocument) = Unit
        override fun pendingDraft(linkId: String): Flow<ConfigDocument?> = pendingFlow
        override suspend fun appliedConfig(linkId: String): ConfigDocument? = appliedSource()
        override fun observeAppliedConfig(linkId: String): Flow<ConfigDocument?> = flowOf(appliedSource())
        override suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError> {
            pushCalls.add(linkId); return pushOutcome
        }
        override suspend fun discardPending(linkId: String) = Unit
    }

    /** Fake editor that returns a sequence of outcomes from pushPending. */
    private class SequentialPushEditor(
        private val outcomes: List<Outcome<Unit, ConfigSyncError>>,
    ) : ConfigEditor {
        var pushCount = 0
        private val pendingFlow: MutableStateFlow<ConfigDocument?> = MutableStateFlow(null)
        override suspend fun updateDraft(linkId: String, mutator: (ConfigDocument) -> ConfigDocument) = Unit
        override fun pendingDraft(linkId: String): Flow<ConfigDocument?> = pendingFlow
        override suspend fun appliedConfig(linkId: String): ConfigDocument? = null
        override fun observeAppliedConfig(linkId: String): Flow<ConfigDocument?> = flowOf(null)
        override suspend fun pushPending(linkId: String): Outcome<Unit, ConfigSyncError> {
            val idx = pushCount.coerceAtMost(outcomes.size - 1)
            pushCount++
            return outcomes[idx]
        }
        override suspend fun discardPending(linkId: String) = Unit
    }
}
