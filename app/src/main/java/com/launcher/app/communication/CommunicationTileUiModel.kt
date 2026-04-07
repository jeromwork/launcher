package com.launcher.app.communication

import com.launcher.api.CommunicationActionType
import com.launcher.api.MockCommunicationEntry

data class CommunicationTileUiModel(
    val tileId: String,
    val contactRef: String,
    val displayName: String,
    val actions: Set<CommunicationActionType>,
)

fun MockCommunicationEntry.toUiModel(displayName: String): CommunicationTileUiModel =
    CommunicationTileUiModel(
        tileId = tileId,
        contactRef = contactRef,
        displayName = displayName,
        actions = capability,
    )

