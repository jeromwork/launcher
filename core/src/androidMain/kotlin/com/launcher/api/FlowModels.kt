package com.launcher.api

data class FlowDescriptor(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val templateId: String,
    val slots: List<SlotDescriptor>,
)

data class SlotDescriptor(
    val id: String,
    val label: String,
    val iconRef: String,
    val action: SlotAction,
)

sealed class SlotAction {
    data class WhatsAppCall(
        val contactRef: String,
        val actionType: CommunicationActionType,
    ) : SlotAction()

    data class OpenApp(val packageName: String) : SlotAction()

    object Placeholder : SlotAction()
}

data class FlowTemplate(
    val id: String,
    val labelResKey: String,
    val availableInPresets: Set<String>,
)
