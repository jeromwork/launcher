package com.launcher.core.flows

import android.content.Context
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowPreset
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.PresetRepository
import com.launcher.api.SlotDescriptor
import com.launcher.api.action.Action
import com.launcher.api.action.ActionWireFormat
import org.json.JSONObject

/**
 * Reads a mock JSON per active preset (asset filename: flows_mock_<slug>.json).
 * Falls back to simple-launcher if preset is unset.
 *
 * Slot `action` field is parsed via [ActionWireFormat]. `null` action =
 * placeholder slot. Malformed entries are tolerated (treated as placeholder).
 */
class MockFlowRepository(
    private val context: Context,
    private val presetRepository: PresetRepository,
) : FlowRepository {

    private val allTemplates = listOf(
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

    override suspend fun loadFlows(): List<FlowDescriptor> {
        val preset = presetRepository.getActivePreset() ?: FlowPreset.SIMPLE_LAUNCHER
        return parseFlows("flows_mock_${preset.slug}.json")
    }

    override fun availableTemplates(presetId: String): List<FlowTemplate> =
        allTemplates.filter { presetId in it.availableInPresets }

    private fun parseFlows(assetFileName: String): List<FlowDescriptor> {
        val raw = runCatching {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        }.getOrElse { return emptyList() }

        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        if (root.optInt("schemaVersion", 0) != 1) return emptyList()

        val flowsArray = root.optJSONArray("flows") ?: return emptyList()
        val result = mutableListOf<FlowDescriptor>()

        for (i in 0 until flowsArray.length()) {
            val flowObj = flowsArray.optJSONObject(i) ?: continue
            val flowId = flowObj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val flowName = flowObj.optString("name").takeIf { it.isNotBlank() } ?: continue
            val templateId = flowObj.optString("templateId").ifBlank { "contacts" }

            val slotsArray = flowObj.optJSONArray("slots") ?: continue
            val slots = mutableListOf<SlotDescriptor>()

            for (j in 0 until slotsArray.length()) {
                val slotObj = slotsArray.optJSONObject(j) ?: continue
                val slotId = slotObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val label = slotObj.optString("label").takeIf { it.isNotBlank() } ?: continue
                val iconRef = slotObj.optString("iconRef")
                val action = parseAction(slotObj.opt("action"))
                slots.add(SlotDescriptor(id = slotId, label = label, iconRef = iconRef, action = action))
            }

            result.add(
                FlowDescriptor(
                    schemaVersion = 1,
                    id = flowId,
                    name = flowName,
                    templateId = templateId,
                    slots = slots,
                ),
            )
        }
        return result
    }

    /**
     * Returns null for placeholder slots (`null` JSON value); throws nothing
     * on malformed entries — the slot is treated as a placeholder so the
     * flow loads even with one bad row. Production assets are validated by
     * tests.
     */
    private fun parseAction(actionValue: Any?): Action? {
        if (actionValue == null || actionValue == JSONObject.NULL) return null
        if (actionValue !is JSONObject) return null
        return runCatching { ActionWireFormat.decode(actionValue.toString()) }.getOrNull()
    }
}
