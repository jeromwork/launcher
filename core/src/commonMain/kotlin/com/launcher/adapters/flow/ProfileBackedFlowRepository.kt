package com.launcher.adapters.flow

import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.SlotDescriptor
import com.launcher.api.action.Action
import com.launcher.api.action.ActionPayload
import com.launcher.api.action.ProviderId
import com.launcher.api.localization.StringResolver
import com.launcher.preset.model.Component
import com.launcher.preset.model.Entity
import com.launcher.preset.model.Profile
import com.launcher.preset.port.ProfileStore
import com.launcher.preset.query.flows
import com.launcher.preset.query.homeScreenTiles
import com.launcher.preset.query.tilesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * TASK-127 (FR-006) — production [FlowRepository] reading the home-screen layout
 * straight from the [Profile] the wizard writes.
 *
 * **Why this exists**: TASK-126 moved the wizard onto the TASK-120 `Profile`
 * model, but the home screen still read `ConfigDocument` — a model nothing was
 * filling any more. The gap surfaced as the TASK-52 "Не удалось загрузить
 * настройки" Error UI after a fresh wizard run. Rather than bridging Profile →
 * ConfigDocument (a bridge onto the model being retired), the home screen now
 * reads the Profile directly through the query API.
 *
 * **Projection**: `Workspace → Flow → tiles` maps onto the existing
 * [FlowDescriptor] shape (a flow with its slots) — no port change, no UI change.
 * A profile with no `Flow` entities (the simple-launcher case, US-1) yields a
 * single synthetic descriptor holding every tile: the one-level screen is the
 * same code path, not a special case.
 *
 * Replaces `ConfigBackedFlowRepository` in DI; that class stays for the future
 * admin-push path (see SRV-CONFIG-DEPRECATION).
 */
class ProfileBackedFlowRepository(
    private val profileStore: ProfileStore,
    /**
     * Turns the `labelKey` / `titleKey` carried by a Component into display text.
     *
     * The Profile stores **keys**, not translated strings — that is what keeps a
     * preset shareable across locales (rule 9) and identity-free. Resolution
     * therefore has to happen on the way out, here at the boundary.
     *
     * [StringResolver] is a domain port (`api/localization`), not an Android type,
     * so this adapter stays platform-free (rule 1). Defaults to a pass-through so
     * tests and non-localized callers keep working with raw keys.
     */
    private val strings: StringResolver = PassThroughStringResolver,
) : FlowRepository {

    /**
     * One-shot load — **this is the path the TASK-52 regression fired on**:
     * [com.launcher.ui.navigation.HomeComponent] calls it with a 3s timeout and
     * renders `Error` if it yields nothing.
     *
     * Awaits the first non-null Profile. Post-wizard the Profile is already
     * saved, so this returns immediately. If a Profile never arrives (wizard
     * skipped or crashed before saving) this suspends and the caller's existing
     * timeout produces `Error` + Retry — deliberate: no silent forever-Loading.
     */
    override suspend fun loadFlows(): List<FlowDescriptor> =
        profileStore.observe().filterNotNull().first().toFlowDescriptors()

    /**
     * Hot path. A transient `null` (cold start, disk read not finished) yields no
     * emission, so `HomeComponent` stays in `Loading` rather than flashing
     * `Error` (SEQ-4). Every later Profile edit re-emits — Settings changes reach
     * the screen without an Activity restart.
     */
    override fun observeFlows(): Flow<List<FlowDescriptor>> =
        profileStore.observe()
            .filterNotNull()
            .map { profile -> profile.toFlowDescriptors() }

    /** Parity with the config-backed implementation: the static template catalogue. */
    override fun availableTemplates(presetId: String): List<FlowTemplate> =
        ALL_TEMPLATES.filter { presetId in it.availableInPresets }

    /**
     * Not supported yet — parity with `ConfigBackedFlowRepository.addFlow`, which
     * also throws. Adding a tile writes to the Profile through the engine, not
     * through this read-side adapter.
     *
     * TODO(profile-add-flow): implement via ProfileStore + ReconcileEngine — TASK-134.
     */
    override suspend fun addFlow(templateId: String): FlowDescriptor =
        error("ProfileBackedFlowRepository.addFlow not supported yet — see TASK-134 (add-flow UX)")

    // ---- projection ----

    private fun Profile.toFlowDescriptors(): List<FlowDescriptor> {
        val flowEntities = flows()
        if (flowEntities.isEmpty()) {
            // Degenerate profile (US-1): tiles with no Flow parent.
            val tiles = homeScreenTiles()
            if (tiles.isEmpty()) return emptyList()
            return listOf(
                FlowDescriptor(
                    schemaVersion = schemaVersion,
                    id = DEFAULT_FLOW_ID,
                    name = "",
                    templateId = DEFAULT_TEMPLATE_ID,
                    slots = tiles.map { it.toSlot() },
                ),
            )
        }
        return flowEntities.map { flowEntity ->
            val titleKey = (flowEntity.component as? Component.Flow)?.titleKey.orEmpty()
            FlowDescriptor(
                schemaVersion = schemaVersion,
                id = flowEntity.id,
                name = if (titleKey.isEmpty()) "" else strings.resolve(titleKey),
                templateId = DEFAULT_TEMPLATE_ID,
                slots = tilesOf(flowEntity.id).map { it.toSlot() },
            )
        }
    }

    /**
     * Entity → tile. `action = null` renders a non-interactive placeholder card
     * (existing [SlotDescriptor] contract), which is the honest result for a
     * component we cannot turn into a tap action.
     */
    private fun Entity.toSlot(): SlotDescriptor = when (val c = component) {
        is Component.AppTile -> SlotDescriptor(
            id = id,
            label = strings.resolve(c.labelKey),
            iconRef = c.iconKey.orEmpty(),
            action = Action(
                providerId = ProviderId.APP,
                payload = ActionPayload.OpenApp(packageHint = c.packageName),
            ),
        )
        // SOS dispatch is owned by the safety stack, not by a generic tile action.
        // Rendering it as a placeholder keeps it visible without inventing a
        // provider contract here.
        else -> SlotDescriptor(
            id = id,
            label = "",
            iconRef = "",
            action = null,
        )
    }

    /** Returns the key unchanged — used when no resolver is supplied (tests, previews). */
    private object PassThroughStringResolver : StringResolver {
        override fun resolve(key: String, args: Map<String, Any>): String = key
        override fun resolvePlural(key: String, count: Int, args: Map<String, Any>): String = key
        override fun currentLocaleTag(): String = "en"
    }

    private companion object {
        const val DEFAULT_FLOW_ID = "default"
        const val DEFAULT_TEMPLATE_ID = "contacts"

        /** Mirrors ConfigBackedFlowRepository.ALL_TEMPLATES (spec 005 / 009 catalogue). */
        val ALL_TEMPLATES = listOf(
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
