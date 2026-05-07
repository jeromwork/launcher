package com.launcher.core.actions

import android.content.Context
import com.launcher.api.ReturnContextRecord
import org.json.JSONObject

class ReturnContextStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(record: ReturnContextRecord) {
        val payload = JSONObject()
            .put("schemaVersion", record.schemaVersion)
            .put("initiatingTileRef", record.initiatingTileRef)
            .put("homeSurfaceRef", record.homeSurfaceRef)
            .put("actionCycleRef", record.actionCycleRef)
            .put("savedAtEpochMs", record.savedAtEpochMs)
            .toString()
        prefs.edit().putString(KEY_ACTIVE_CONTEXT, payload).apply()
    }

    fun load(): ReturnContextRecord? {
        val raw = prefs.getString(KEY_ACTIVE_CONTEXT, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            ReturnContextRecord(
                schemaVersion = j.optInt("schemaVersion", 1),
                initiatingTileRef = j.getString("initiatingTileRef"),
                homeSurfaceRef = j.getString("homeSurfaceRef"),
                actionCycleRef = j.getString("actionCycleRef"),
                savedAtEpochMs = j.getLong("savedAtEpochMs"),
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_ACTIVE_CONTEXT).apply()
    }

    companion object {
        private const val PREFS_NAME = "launcher.communication.return_context"
        private const val KEY_ACTIVE_CONTEXT = "active_context_json"
    }
}

