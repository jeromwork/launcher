package com.launcher.api

import kotlinx.coroutines.flow.Flow

interface FlowRepository {
    suspend fun loadFlows(): List<FlowDescriptor>
    fun availableTemplates(presetId: String): List<FlowTemplate>

    /**
     * Spec 010 T029 / ARCH-016 closure — hot Flow of the device's current
     * flow layout. Emits on subscribe + re-emits whenever the underlying
     * `/links/{linkId}/config/current` is replaced (admin push → ConfigApplier
     * → LocalConfigStore.writeAppliedConfig → this Flow).
     *
     * Implementations:
     *  - `ConfigBackedFlowRepository` (real, androidMain): observes
     *    [com.launcher.api.config.ConfigEditor.observeAppliedConfig] for the
     *    current Managed-side linkId from [com.launcher.api.link.LinkRegistry]
     *    and maps `ConfigDocument` → `List<FlowDescriptor>` per FR-003 via
     *    [com.launcher.api.action.SlotToActionMapper].
     *  - Tests bind via Koin к `FakeRemoteSyncBackend`-backed `FakeConfigEditor`
     *    + `FakeLinkRegistry` (no direct `Fake` FlowRepository class — there
     *    used to be `MockFlowRepository` in spec 005, deleted by T031/T032).
     */
    fun observeFlows(): Flow<List<FlowDescriptor>>

    /**
     * Spec 007 wizard support — append a new flow created from [templateId].
     * The default label is derived from the template; renames happen later
     * via slot editing UI. Returns the freshly-added flow.
     */
    suspend fun addFlow(templateId: String): FlowDescriptor
}
