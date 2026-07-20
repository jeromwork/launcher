package com.launcher.adapters.config

import family.wire.WireVersion

import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.SlotDescriptor
import com.launcher.api.action.toAction
import com.launcher.api.config.ConfigDocument
import com.launcher.api.config.ConfigEditor
import com.launcher.api.link.LinkRegistry
import com.launcher.api.paired.LocalLinkRevocationStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * TODO(config-deprecation, SRV-CONFIG-DEPRECATION): ConfigDocument stays for
 * admin push (spec 009); remove entirely when unified Profile-sync ships.
 *
 * **No longer bound to [FlowRepository] as of TASK-127** — the home screen now
 * reads the `Profile` the wizard writes, via
 * [com.launcher.adapters.flow.ProfileBackedFlowRepository]. This class is kept
 * (with its tests) because the admin-push path still speaks ConfigDocument.
 *
 * Spec 010 ARCH-016 closure — production [FlowRepository] that reads
 * the home-screen layout from `/links/{linkId}/config/current` (spec 008
 * applied config), mapping each [com.launcher.api.config.Slot] to a fully-formed
 * [com.launcher.api.action.Action] via the [toAction] free function.
 *
 * Replaces `MockFlowRepository` (deleted by spec 010 T031/T032). The bundled
 * `flows_mock_*.json` assets are gone — initial layout for unpaired devices
 * is seeded by the preset picker into `/config/current` via `ConfigApplier`
 * (spec 008), and the wizard / pairing flow guarantees a populated document
 * before the home screen shows non-empty content.
 *
 * Two pieces of state:
 *  - [linkRegistry] currentLink — the active Managed-side link (null when
 *    not paired).
 *  - [configEditor] observeAppliedConfig(linkId) — the layout document for
 *    that link.
 *
 * When unpaired OR before first apply, [observeFlows] emits an empty list
 * and `HomeScreen` shows its «Загрузка…» state.
 *
 * `availableTemplates` keeps the spec 005 / spec 009 template catalogue
 * inline (small + stable) — the wizard's «add flow» picker still queries
 * it by preset slug.
 */
class ConfigBackedFlowRepository(
    private val configEditor: ConfigEditor,
    private val linkRegistry: LinkRegistry,
    private val revocationStore: LocalLinkRevocationStore? = null,
) : FlowRepository {

    override suspend fun loadFlows(): List<FlowDescriptor> {
        val linkId = activeLinkId() ?: return emptyList()
        val applied = configEditor.appliedConfig(linkId) ?: return emptyList()
        return applied.toFlowDescriptors()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFlows(): Flow<List<FlowDescriptor>> =
        activeLinkFlow()
            .flatMapLatest { linkId ->
                if (linkId == null) {
                    flowOf(emptyList())
                } else {
                    configEditor.observeAppliedConfig(linkId)
                        .map { it?.toFlowDescriptors().orEmpty() }
                }
            }

    /**
     * Spec 010 FR-032: фильтрует locally-revoked link out so HomeScreen
     * перестаёт получать `/config` push'и для отозванного линка ещё до того,
     * как WorkManager успел добежать до server-side `LinkRegistry.revoke()`.
     */
    private fun activeLinkFlow(): Flow<String?> {
        val store = revocationStore ?: return linkRegistry.currentLink().map { it?.linkId }
        return combine(linkRegistry.currentLink(), store.revokedLinkIds()) { link, revoked ->
            link?.takeIf { it.linkId !in revoked }?.linkId
        }
    }

    private suspend fun activeLinkId(): String? {
        val current = linkRegistry.currentLink().first()?.linkId ?: return null
        val revoked = revocationStore?.revokedLinkIds()?.first().orEmpty()
        return current.takeIf { it !in revoked }
    }

    override fun availableTemplates(presetId: String): List<FlowTemplate> =
        ALL_TEMPLATES.filter { presetId in it.availableInPresets }

    // Spec 007 `FlowRepository.addFlow` — ConfigBackedFlowRepository не подходит
    // для wizard-driven additions (single source of truth = /config/current,
    // editing идёт через ConfigEditor). Bypass: error для unsupported operation.
    // wizard flow в спеке 007 originally работал с MockFlowRepository (удалён в
    // спеке 010). Если wizard вернётся — нужен ConfigEditor.addFlow API.
    override suspend fun addFlow(templateId: String): com.launcher.api.FlowDescriptor =
        error("ConfigBackedFlowRepository.addFlow not supported — wizard добавление flow должно идти через ConfigEditor (spec 010 ARCH-016)")

    private fun ConfigDocument.toFlowDescriptors(): List<FlowDescriptor> =
        flows.map { flow ->
            FlowDescriptor(
                schemaVersion = schemaVersion,
                id = flow.id.value,
                name = flow.title,
                // Spec 010 keeps the spec 005 `templateId` field for backward
                // compat with AddSlotWizard; the new config doesn't carry one,
                // so we default to "contacts" (the universal template).
                templateId = "contacts",
                slots = flow.slots.map { slot ->
                    SlotDescriptor(
                        id = slot.id.value,
                        // ConfigDocument doesn't carry per-slot labels; the
                        // label comes from the resolved contact name for
                        // call/sms slots, package label for open-app slots.
                        // Tile rendering at the TileCard layer reads
                        // contact / package, so an empty label here is safe.
                        label = labelFor(slot, contacts),
                        iconRef = "",
                        action = slot.toAction(contacts),
                    )
                },
            )
        }

    private fun labelFor(
        slot: com.launcher.api.config.Slot,
        contacts: List<com.launcher.api.config.Contact>,
    ): String {
        val args = slot.args ?: return ""
        val contactId = (args["contactId"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }?.content
        if (contactId != null) {
            return contacts.firstOrNull { it.id.value == contactId }?.displayName.orEmpty()
        }
        val packageName = (args["packageName"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }?.content
        return packageName.orEmpty()
    }

    companion object {
        private val ALL_TEMPLATES = listOf(
            FlowTemplate(
                id = "contacts",
                labelResKey = "flow_template_contacts",
                availableInPresets = setOf("simple-launcher", "workspace", "launcher"),
            ),
            FlowTemplate(
                id = "admin_devices",
                labelResKey = "flow_template_admin_devices",
                availableInPresets = setOf("workspace"),
            ),
        )
    }
}
