package com.launcher.api

import com.launcher.api.action.Action
import com.launcher.wire.WireVersion

data class FlowDescriptor(
    val schemaVersion: WireVersion,
    val id: String,
    val name: String,
    val templateId: String,
    val slots: List<SlotDescriptor>,
)

/**
 * One tile on the home / flow screen. [action] is `null` when the slot is a
 * placeholder (renders an empty card with no tap behaviour). When non-null,
 * it carries a fully-formed [Action] ready to hand to the dispatcher.
 */
data class SlotDescriptor(
    val id: String,
    val label: String,
    val iconRef: String,
    val action: Action?,
)

data class FlowTemplate(
    val id: String,
    val labelResKey: String,
    val availableInPresets: Set<String>,
)
