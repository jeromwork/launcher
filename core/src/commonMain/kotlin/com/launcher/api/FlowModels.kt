package com.launcher.api

import com.launcher.api.action.Action

data class FlowDescriptor(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val templateId: String,
    val slots: List<SlotDescriptor>,
)

/**
 * One tile on the home / flow screen. [action] is `null` when the slot is a
 * placeholder (renders an empty card with no tap behaviour). When non-null,
 * it carries a fully-formed [Action] ready to hand to `ActionDispatcher`.
 *
 * Spec 005 migration note: `SlotAction` (sealed: WhatsAppCall / OpenApp /
 * Placeholder) used to live here. It is replaced by [Action] (the
 * provider-agnostic dispatch root from spec 005) plus a nullable for
 * placeholders. The legacy [SlotAction] type stays in the file below as
 * `@Deprecated` until Phase 6 deletion lands — old tests still reference it.
 */
data class SlotDescriptor(
    val id: String,
    val label: String,
    val iconRef: String,
    val action: Action?,
)

/**
 * @deprecated Replaced by [Action] (spec 005). Kept temporarily so that
 * [com.launcher.core.actions.ActionDispatcher] (spec 003 path) and its tests
 * still compile while the new dispatcher takes over. Phase 6 of spec 005
 * removes this entirely.
 */
@Deprecated("Replaced by com.launcher.api.action.Action; see spec 005 §6.3")
sealed class SlotAction {
    @Deprecated("Replaced by com.launcher.api.action.ActionPayload.WhatsAppCall")
    data class WhatsAppCall(
        val contactRef: String,
        val actionType: CommunicationActionType,
    ) : SlotAction()

    @Deprecated("Replaced by com.launcher.api.action.ActionPayload.OpenApp")
    data class OpenApp(val packageName: String) : SlotAction()

    @Deprecated("A placeholder slot is now expressed as SlotDescriptor.action == null")
    object Placeholder : SlotAction()
}

data class FlowTemplate(
    val id: String,
    val labelResKey: String,
    val availableInPresets: Set<String>,
)
