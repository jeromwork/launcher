package com.launcher.core.actions

import android.content.Context
import com.launcher.api.ControlMode
import org.json.JSONArray
import org.json.JSONObject

data class SafeControlConfig(
    val defaultMode: ControlMode,
    val allowedMessengerPackages: Set<String>,
    val caregiverPin: String,
)

class SafeControlConfigStore(
    private val context: Context,
) {
    fun load(): SafeControlConfig {
        val json = runCatching {
            context.assets.open(CONFIG_ASSET_FILE).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return defaultConfig()

        val root = runCatching { JSONObject(json) }.getOrNull() ?: return defaultConfig()
        val mode = runCatching {
            ControlMode.valueOf(root.optString(KEY_DEFAULT_MODE, ControlMode.STANDARD.name))
        }.getOrDefault(ControlMode.STANDARD)
        val messengerPackages = root.optJSONArray(KEY_ALLOWED_MESSENGERS).toStringSet()
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty { DEFAULT_MESSENGERS }
        val pin = root.optString(KEY_CAREGIVER_PIN, DEFAULT_CAREGIVER_PIN).ifBlank {
            DEFAULT_CAREGIVER_PIN
        }
        return SafeControlConfig(
            defaultMode = mode,
            allowedMessengerPackages = messengerPackages,
            caregiverPin = pin,
        )
    }

    private fun defaultConfig(): SafeControlConfig = SafeControlConfig(
        defaultMode = ControlMode.STANDARD,
        allowedMessengerPackages = DEFAULT_MESSENGERS,
        caregiverPin = DEFAULT_CAREGIVER_PIN,
    )

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) {
            return emptySet()
        }
        val out = LinkedHashSet<String>()
        for (i in 0 until length()) {
            out += optString(i)
        }
        return out
    }

    companion object {
        private const val CONFIG_ASSET_FILE = "safe_control_policy.json"
        private const val KEY_DEFAULT_MODE = "defaultControlMode"
        private const val KEY_ALLOWED_MESSENGERS = "allowedMessengerPackages"
        private const val KEY_CAREGIVER_PIN = "caregiverPin"
        private const val DEFAULT_CAREGIVER_PIN = "2580"
        private val DEFAULT_MESSENGERS = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
        )
    }
}

