package com.launcher.api.wizard

import kotlinx.serialization.Serializable

@Serializable
data class DismissedHintsState(
    val ids: Set<String> = emptySet(),
)

interface DismissedHintsStore {
    suspend fun isDismissed(hintId: String): Boolean
    suspend fun markDismissed(hintId: String)
    suspend fun clear(hintId: String)
    suspend fun current(): DismissedHintsState
}
