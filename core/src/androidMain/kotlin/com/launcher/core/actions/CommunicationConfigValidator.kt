package com.launcher.core.actions

import android.content.Context
import com.launcher.api.CommunicationActionType
import com.launcher.api.MockCommunicationEntry
import org.json.JSONArray

class CommunicationConfigValidator(
    private val context: Context,
    private val assetFileName: String = "whatsapp_tiles_mock.json",
) {
    private val entries: List<MockCommunicationEntry> by lazy { loadEntries() }

    fun loadValidatedEntries(): List<MockCommunicationEntry> = entries

    fun isActionSupported(contactRef: String, actionType: CommunicationActionType): Boolean {
        val entry = entries.firstOrNull { it.contactRef == contactRef } ?: return false
        return entry.capability.contains(actionType)
    }

    private fun loadEntries(): List<MockCommunicationEntry> {
        val raw = runCatching {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        }.getOrElse { return emptyList() }
        val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        val out = mutableListOf<MockCommunicationEntry>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val tileId = item.optString("tileId")
            val contactRef = item.optString("contactRef")
            val displayNameKey = item.optString("displayNameKey")
            if (tileId.isBlank() || contactRef.isBlank() || displayNameKey.isBlank()) continue

            val capabilitiesJson = item.optJSONArray("capability") ?: JSONArray()
            val capabilities = mutableSetOf<CommunicationActionType>()
            for (j in 0 until capabilitiesJson.length()) {
                val rawAction = capabilitiesJson.optString(j)
                val action = runCatching { CommunicationActionType.valueOf(rawAction) }.getOrNull()
                if (action != null) capabilities.add(action)
            }
            if (capabilities.isEmpty()) continue

            out.add(
                MockCommunicationEntry(
                    tileId = tileId,
                    contactRef = contactRef,
                    displayNameKey = displayNameKey,
                    photoRef = item.optString("photoRef").takeIf { it.isNotBlank() },
                    capability = capabilities,
                ),
            )
        }
        return out
    }
}

