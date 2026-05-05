package com.launcher.core.flows

import android.content.Context
import com.launcher.api.CommunicationActionType
import com.launcher.api.FlowDescriptor
import com.launcher.api.FlowRepository
import com.launcher.api.FlowTemplate
import com.launcher.api.SlotAction
import com.launcher.api.SlotDescriptor
import org.json.JSONObject

class MockFlowRepository(
    private val context: Context,
    private val assetFileName: String = "flows_mock.json",
) : FlowRepository {

    private val allTemplates = listOf(
        FlowTemplate(
            id = "contacts",
            labelResKey = "flow_template_contacts",
            availableInPresets = setOf("senior-launcher", "flow-light"),
        ),
        FlowTemplate(
            id = "admin_devices",
            labelResKey = "flow_template_admin_devices",
            availableInPresets = setOf("flow-light"),
        ),
    )

    override suspend fun loadFlows(): List<FlowDescriptor> = parseFlows()

    override fun availableTemplates(presetId: String): List<FlowTemplate> =
        allTemplates.filter { presetId in it.availableInPresets }

    private fun parseFlows(): List<FlowDescriptor> {
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
                val action = parseAction(slotObj.optJSONObject("action")) ?: continue
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

    private fun parseAction(actionObj: JSONObject?): SlotAction? {
        actionObj ?: return null
        return when (val type = actionObj.optString("type")) {
            "whatsapp_call" -> {
                val contactRef = actionObj.optString("contactRef").takeIf { it.isNotBlank() } ?: return null
                val actionTypeRaw = actionObj.optString("actionType")
                val actionType = runCatching { CommunicationActionType.valueOf(actionTypeRaw) }.getOrNull()
                    ?: return null
                SlotAction.WhatsAppCall(contactRef = contactRef, actionType = actionType)
            }
            "open_app" -> {
                val pkg = actionObj.optString("packageName").takeIf { it.isNotBlank() } ?: return null
                SlotAction.OpenApp(packageName = pkg)
            }
            else -> {
                if (type == "placeholder") SlotAction.Placeholder else null
            }
        }
    }
}
